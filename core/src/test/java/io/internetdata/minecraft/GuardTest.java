package io.internetdata.minecraft;

import io.internetdata.InternetData;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuardTest {
    private static final String TOR_IP = "45.83.91.1";
    private static final String HOSTING_IP = "146.70.116.130";
    private static final UUID PLAYER = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");
    private static final String NOTHING = "checks: {vpn: allow, tor: allow, hosting: allow, proxy: allow, "
            + "relay: allow, carrier: allow, satellite: allow, anycast: allow}\n";

    @TempDir
    Path dir;
    private StubApi api;
    private InternetData client;
    private final List<String> warnings = new CopyOnWriteArrayList<>();

    @BeforeEach
    void start() throws Exception {
        api = new StubApi();
        client = InternetData.builder().baseUrl(api.url()).httpClient(new UserAgentHttpClient("test/1")).build();
    }

    @AfterEach
    void stop() {
        client.close();
        api.close();
    }

    private Guard guard(String yaml, String bypass) throws Exception {
        List<String> problems = new ArrayList<>();
        Settings settings = Settings.parse(ConfigFile.parse(yaml), ConfigFile.defaults(), problems);
        assertEquals(List.of(), problems);
        Path file = dir.resolve("bypass.txt");
        Files.writeString(file, bypass);
        return new Guard(settings, Bypass.load(file, problems), client, warnings::add);
    }

    private Verdict check(Guard guard, String ip) throws Exception {
        return guard.check("Notch", PLAYER, InetAddress.getByName(ip));
    }

    @Test
    void theShippedDefaultsBlockTorWithNoKey() throws Exception {
        api.answer("/" + TOR_IP, Answers.unlimited(TOR_IP, "tor"));
        Verdict verdict = check(guard("", ""), TOR_IP);
        assertEquals(Action.BLOCK, verdict.action());
        assertEquals("Tor", verdict.reason());
    }

    @Test
    void aCheckThePlanDoesNotAnswerIsReportedAndNeverMatches() throws Exception {
        api.answer("/" + TOR_IP, Answers.unlimited(TOR_IP));
        assertEquals(Action.ALLOW, check(guard("", ""), TOR_IP).action());
        assertEquals(2, warnings.size(), warnings.toString());
        assertTrue(warnings.get(0).startsWith("checks.vpn is block, but it needs a paid plan"), warnings.get(0));
        assertTrue(warnings.get(1).startsWith("checks.proxy is block") && warnings.get(1).contains("/inetd login"),
                warnings.get(1));
    }

    @Test
    void anAnswerWithNoKeyFindsAHostingNetworkByTheTypeOfItsAs() throws Exception {
        api.answer("/" + HOSTING_IP, Answers.unlimitedAt(HOSTING_IP, Answers.LOCATION_AT, Answers.ASN_M247));
        Verdict verdict = check(guard("checks: {vpn: allow, proxy: allow, hosting: alert}", ""), HOSTING_IP);
        assertEquals(Action.ALERT, verdict.action());
        assertEquals("hosting network (M247 Europe SRL)", verdict.reason());
        assertEquals(List.of(), warnings);
    }

    @Test
    void theFreeAnswersOwnFlagsAlertStaffByDefault() throws Exception {
        String ip = "98.97.1.2";
        api.answer("/" + ip, Answers.unlimitedAt(ip, Answers.LOCATION_BR, Answers.ASN_STARLINK, "relay", "carrier",
                "satellite", "anycast"));
        Verdict verdict = check(guard("checks: {vpn: allow, proxy: allow}", ""), ip);
        assertEquals(Action.ALERT, verdict.action());
        assertEquals(List.of("privacy relay (iCloud Private Relay)", "mobile network (SpaceX Services, Inc.)",
                "satellite (SpaceX Services, Inc.)", "anycast (SpaceX Services, Inc.)"), verdict.reasons());
        assertEquals(List.of(), warnings);
    }

    @Test
    void aPaidAnswerNamesTheCarrierAndTheSatelliteOperatorItself() throws Exception {
        String ip = "98.97.1.2";
        api.answer("/" + ip, Answers.paidClean(ip, Answers.LOCATION_BR, Answers.ASN_STARLINK,
                "carrier", "{\"name\":\"Claro\",\"mcc\":\"724\",\"mnc\":\"05\",\"country\":\"BR\","
                        + "\"confidence\":\"high\"}",
                "satellite", "{\"provider\":\"starlink\",\"provider_name\":\"Starlink\",\"orbit\":\"leo\","
                        + "\"segment\":\"consumer\",\"country\":\"BR\",\"asn\":14593,\"confidence\":\"high\"}"));
        Verdict verdict = check(guard("checks: {carrier: block}", ""), ip);
        assertEquals(Action.BLOCK, verdict.action());
        assertEquals(List.of("mobile network (Claro)"), verdict.reasons());
        assertEquals(Action.ALERT, check(guard("checks: {carrier: allow}", ""), ip).action());
    }

    @Test
    void aFreePlanKeyPastItsDailyTrialIsAnsweredAsUnlimitedAndSaysWhatItLacks() throws Exception {
        api.answer("/" + HOSTING_IP,
                Answers.trial(Answers.unlimitedAt(HOSTING_IP, Answers.LOCATION_AT, Answers.ASN_M247), true));
        Verdict verdict = check(guard("api-key: \"id_test_0123456789abcdef\"\nchecks: {hosting: block}", ""),
                HOSTING_IP);
        assertEquals(Action.BLOCK, verdict.action());
        assertEquals("hosting network (M247 Europe SRL)", verdict.reason());
        assertTrue(warnings.stream().anyMatch(w -> w.startsWith("checks.vpn is block") && w.contains("free plan")),
                warnings.toString());
    }

    @Test
    void aTrialKeyWithinItsDayIsAnsweredInFull() throws Exception {
        api.answer("/" + HOSTING_IP, Answers.trial(Answers.paid(HOSTING_IP), false));
        Verdict verdict = check(guard("checks: {vpn: alert, hosting: block, proxy: block}", ""), HOSTING_IP);
        assertEquals(Action.BLOCK, verdict.action());
        assertEquals(List.of("hosting (M247)", "residential proxy (Bright Data)"), verdict.reasons());
        assertEquals(List.of(), warnings);
    }

    @Test
    void aPaidPlanNamesEachProviderAndBlockingBeatsAlerting() throws Exception {
        api.answer("/" + HOSTING_IP, Answers.paid(HOSTING_IP));
        Verdict verdict = check(guard("checks: {vpn: alert, hosting: block, proxy: block}", ""), HOSTING_IP);
        assertEquals(Action.BLOCK, verdict.action());
        assertEquals(List.of("hosting (M247)", "residential proxy (Bright Data)"), verdict.reasons());
        assertFalse(verdict.country());
        Verdict alerted = check(guard("checks: {vpn: alert, hosting: allow, proxy: allow}", ""), HOSTING_IP);
        assertEquals(Action.ALERT, alerted.action());
        assertEquals("VPN (Mullvad)", alerted.reason());
    }

    @Test
    void countriesRefuseByBlockListAndByAllowList() throws Exception {
        String ip = "177.1.2.3";
        api.answer("/" + ip, Answers.unlimited(ip));
        Verdict blocked = check(guard("countries: {block: [br]}", ""), ip);
        assertEquals(Action.BLOCK, blocked.action());
        assertEquals("country (BR)", blocked.reason());
        assertTrue(blocked.country());

        Verdict notAllowed = check(guard("countries: {allow: [US, CA]}", ""), ip);
        assertEquals(Action.BLOCK, notAllowed.action());
        assertTrue(notAllowed.country());

        assertEquals(Action.ALLOW, check(guard("countries: {allow: [BR]}", ""), ip).action());
    }

    @Test
    void anAddressNoCountryCanBePlacedIsLetIn() throws Exception {
        String ip = "177.1.2.3";
        api.answer("/" + ip, Answers.unlimitedAt(ip, Answers.LOCATION_UNKNOWN, Answers.ASN_ISP));
        assertEquals(Action.ALLOW, check(guard("countries: {allow: [US]}", ""), ip).action());
    }

    @Test
    void countriesWorkWithNoKey() throws Exception {
        api.answer("/" + TOR_IP, Answers.unlimited(TOR_IP));
        Verdict verdict = check(guard("countries: {block: [BR]}", ""), TOR_IP);
        assertEquals(Action.BLOCK, verdict.action());
        assertEquals("country (BR)", verdict.reason());
        assertFalse(warnings.stream().anyMatch(w -> w.startsWith("countries")), warnings.toString());
    }

    @Test
    void rulesMatchOnAnyFieldTheSdkMatchesOn() throws Exception {
        api.answer("/" + HOSTING_IP, Answers.paid(HOSTING_IP));
        String yaml = NOTHING + """
                rules:
                  - action: alert
                    reason: busy pool
                    match:
                      resproxy:
                        hits: {gte: 7}
                  - action: block
                    reason: M247
                    match:
                      asn: {asn: 9009}
                  - action: block
                    match:
                      location: {country: [DE, AT]}
                  - action: block
                    reason: never
                    match:
                      vpn: {provider: nordvpn}
                """;
        Verdict verdict = check(guard(yaml, ""), HOSTING_IP);
        assertEquals(Action.BLOCK, verdict.action());
        assertEquals(List.of("M247", "rule #3"), verdict.reasons());
        assertEquals(List.of(), warnings);
    }

    @Test
    void rulesWithNoKeyMatchOnWhatEveryAnswerCarries() throws Exception {
        String ip = "177.1.2.3";
        api.answer("/" + ip, Answers.unlimited(ip, "ssh"));
        String yaml = NOTHING + """
                rules:
                  - action: alert
                    reason: an SSH server
                    match: {is_ssh: true}
                  - action: block
                    reason: Claro
                    match:
                      asn: {name: claro nxt}
                      location: {continent: SA}
                """;
        Verdict verdict = check(guard(yaml, ""), ip);
        assertEquals(Action.BLOCK, verdict.action());
        assertEquals(List.of("Claro"), verdict.reasons());
        assertEquals(List.of(), warnings);
    }

    @Test
    void aRuleNamingAMemberThePlanDoesNotServeIsReported() throws Exception {
        api.answer("/" + TOR_IP, Answers.unlimited(TOR_IP));
        String yaml = NOTHING + """
                rules:
                  - action: block
                    reason: VPNs
                    match: {is_vpn: true}
                """;
        assertEquals(Action.ALLOW, check(guard(yaml, ""), TOR_IP).action());
        assertTrue(warnings.stream().anyMatch(w -> w.contains("'VPNs' names is_vpn")), warnings.toString());
    }

    @Test
    void aBypassedLoginIsNeverLookedUp() throws Exception {
        api.answer("/" + TOR_IP, Answers.unlimited(TOR_IP, "tor"));
        String[] lists = {"notch", PLAYER.toString(), PLAYER.toString().replace("-", ""), TOR_IP, "45.83.91.0/24"};
        for (String entry : lists) {
            Verdict verdict = check(guard("", entry + "  # a comment\n"), TOR_IP);
            assertTrue(verdict.isBypassed(), entry);
            assertEquals(Action.ALLOW, verdict.action(), entry);
        }
        assertEquals(0, api.count("/" + TOR_IP));
        assertEquals(Action.BLOCK, check(guard("", "45.83.92.0/24\nJeb_\n"), TOR_IP).action());
    }

    @Test
    void aFailedLookupLetsThePlayerInUnlessFailClosed() throws Exception {
        api.route("/" + TOR_IP, request -> new StubApi.Answer(500, "{\"error\":\"lookup failed\"}", 0));
        Verdict open = check(guard("", ""), TOR_IP);
        assertEquals(Action.ALLOW, open.action());
        assertTrue(open.error().isPresent());

        Verdict closed = check(guard("fail-closed: true", ""), TOR_IP);
        assertEquals(Action.BLOCK, closed.action());
        assertTrue(closed.error().isPresent());
    }

    @Test
    void anAnswerMissingWhatEveryPlanCarriesIsAFailedLookup() throws Exception {
        api.answer("/" + TOR_IP, "{\"ip\":\"" + TOR_IP + "\",\"is_tor\":true}");
        Verdict verdict = check(guard("", ""), TOR_IP);
        assertEquals(Action.ALLOW, verdict.action());
        assertTrue(verdict.error().isPresent());
    }

    @Test
    void aPrivateAddressIsLetInWithoutALookupAndReportedOnce() throws Exception {
        Guard guard = guard("", "");
        assertEquals(Action.ALLOW, check(guard, "10.0.0.7").action());
        assertEquals(Action.ALLOW, check(guard, "::ffff:192.168.1.20").action());
        assertTrue(api.requests.isEmpty());
        assertEquals(1, warnings.size(), warnings.toString());
        assertTrue(warnings.get(0).contains("10.0.0.7, a private address"), warnings.toString());
    }

    @Test
    void playersJoiningTogetherFromOneAddressShareOneLookup() throws Exception {
        api.route("/" + TOR_IP, request -> new StubApi.Answer(200, Answers.unlimited(TOR_IP, "tor"), 300));
        Guard guard = guard("", "");
        int players = 8;
        ExecutorService pool = Executors.newFixedThreadPool(players);
        CountDownLatch start = new CountDownLatch(1);
        List<CompletableFuture<Verdict>> verdicts = new ArrayList<>();
        for (int i = 0; i < players; i++) {
            UUID uuid = UUID.randomUUID();
            verdicts.add(CompletableFuture.supplyAsync(() -> {
                try {
                    start.await();
                    return guard.check("bot", uuid, InetAddress.getByName(TOR_IP));
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            }, pool));
        }
        start.countDown();
        for (CompletableFuture<Verdict> verdict : verdicts) {
            assertEquals(Action.BLOCK, verdict.get().action());
        }
        pool.shutdown();
        assertEquals(1, api.count("/" + TOR_IP));
        // The next login from it is answered from the cache.
        assertEquals(Action.BLOCK, check(guard, TOR_IP).action());
        assertEquals(1, api.count("/" + TOR_IP));
    }

    @Test
    void aPrefetchIsTheLookupTheLoginThenUses() throws Exception {
        api.route("/" + TOR_IP, request -> new StubApi.Answer(200, Answers.unlimited(TOR_IP, "tor"), 300));
        Guard guard = guard("", "");
        guard.prefetch(InetAddress.getByName(TOR_IP));
        assertEquals(Action.BLOCK, check(guard, TOR_IP).action());
        assertEquals(1, api.count("/" + TOR_IP));
    }

    @Test
    void everyLookupNamesThePluginInItsUserAgent() throws Exception {
        api.answer("/" + TOR_IP, Answers.unlimited(TOR_IP));
        check(guard("", ""), TOR_IP);
        assertEquals("test/1", api.requests.get(0).header("User-Agent"));
    }
}
