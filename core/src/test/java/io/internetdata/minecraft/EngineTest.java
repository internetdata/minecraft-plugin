package io.internetdata.minecraft;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EngineTest {
    private static final String TOR_IP = "45.83.91.1";
    private static final String ENTITLEMENT = "{\"org_id\":\"f32191d0-ef02-450e-a505-eb5814c35cab\","
            + "\"apikey\":{\"id\":\"10c2b437-3aa2-4a63-bd17-8e7c8c7f0def\",\"expires\":null,\"allowed_cidrs\":[]},"
            + "\"plan\":{\"key\":\"starter\",\"tier\":\"starter\",\"name\":\"Starter\"},\"usage\":{\"requests\":545,\"quota\":250000,"
            + "\"hard_limit\":null,\"window_start\":\"2026-11-01T00:00:00Z\",\"window_end\":\"2026-12-01T00:00:00Z\"}}";
    // An organization on the free plan, as the API names it: its usage counts the day's lookups with every field.
    private static final String FREE_PLAN_ENTITLEMENT = "{\"org_id\":\"f32191d0-ef02-450e-a505-eb5814c35cab\","
            + "\"apikey\":{\"id\":\"10c2b437-3aa2-4a63-bd17-8e7c8c7f0def\",\"expires\":null,\"allowed_cidrs\":[]},"
            + "\"plan\":{\"key\":\"free\",\"tier\":\"free\",\"name\":\"Unlimited\"},\"usage\":{\"requests\":45,\"quota\":100,"
            + "\"hard_limit\":null,\"window_start\":\"2026-11-01T00:00:00Z\",\"window_end\":\"2026-12-01T00:00:00Z\"}}";
    private static final String ROUTING = """
            routing:
              continents: {EU: lobby-eu, SA: lobby-sa}
              countries: {BR: lobby-br, NO: lobby-no}
            """;

    @TempDir
    Path dir;
    private StubApi api;
    private FakePlatform platform;
    private Engine engine;

    @BeforeEach
    void start() throws Exception {
        api = new StubApi();
        System.setProperty("internetdata.api-url", api.url());
        platform = new FakePlatform(dir);
    }

    @AfterEach
    void stop() {
        if (engine != null) {
            engine.disable();
        }
        System.clearProperty("internetdata.api-url");
        api.close();
    }

    private Engine enable() {
        engine = new Engine(platform);
        engine.enable();
        return engine;
    }

    private void config(String yaml) throws Exception {
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("config.yml"), yaml);
    }

    @Test
    void theFirstStartWritesTheDefaultsAndSaysWhatItChecks() throws Exception {
        enable();
        assertTrue(Files.readString(dir.resolve("config.yml")).contains("api-key: \"\""));
        assertTrue(Files.readString(dir.resolve("bypass.txt")).startsWith("# Players and addresses"));
        assertEquals("InternetData " + engine.version() + " on Fake 1.0: vpn block, tor block, hosting block, "
                + "proxy block, relay alert, carrier alert, satellite alert, anycast alert", platform.infos.get(0));
        assertTrue(platform.infos.stream().anyMatch(i -> i.startsWith("No API key, so VPNs, proxies, hosting networks")),
                platform.infos.toString());
        assertEquals(List.of(), platform.warnings);
    }

    @Test
    void aRefusedLoginIsToldToStaffAndLoggedWithTheKickMessageItsPlayerSees() throws Exception {
        api.answer("/" + TOR_IP, Answers.unlimited(TOR_IP, "tor"));
        enable();
        InetAddress address = InetAddress.getByName(TOR_IP);
        Verdict verdict = engine.login("Steve", UUID.randomUUID(), address);
        assertTrue(verdict.isBlocked());
        assertEquals("§cYou can't join from this connection: Tor.\n§7Turn off your VPN or proxy and join again.",
                engine.kickMessage(verdict, "Steve", address));
        assertEquals(List.of(Engine.ALERTS
                + " §8[§3InternetData§8] §7Refused §fSteve §7from §f45.83.91.1 §7(BR): §cTor"), platform.notified);
        assertTrue(platform.infos.contains("Refused Steve from 45.83.91.1 (BR): Tor"), platform.infos.toString());
        assertEquals(1, engine.refused.get());
        String userAgent = api.requests.get(0).header("User-Agent");
        assertTrue(userAgent.startsWith("internetdata-minecraft/" + engine.version() + " (Fake 1.0; Java "),
                userAgent);
    }

    @Test
    void aFlaggedLoginIsToldToStaffWithTheirCountry() throws Exception {
        String ip = "98.97.1.2";
        api.answer("/" + ip, Answers.unlimitedAt(ip, Answers.LOCATION_BR, Answers.ASN_STARLINK, "relay"));
        enable();
        Verdict verdict = engine.login("Alex", UUID.randomUUID(), InetAddress.getByName(ip));
        assertEquals(Action.ALERT, verdict.action());
        assertEquals(List.of(Engine.ALERTS + " §8[§3InternetData§8] §fAlex §7joined from §f98.97.1.2 §7(BR): "
                + "§eprivacy relay (iCloud Private Relay)"), platform.notified);
        assertEquals(1, engine.flagged.get());
    }

    @Test
    void anEmptyMessageTurnsItsAlertOff() throws Exception {
        api.answer("/" + TOR_IP, Answers.unlimited(TOR_IP, "tor"));
        config("messages: {blocked: \"\"}\n");
        enable();
        assertTrue(engine.login("Steve", UUID.randomUUID(), InetAddress.getByName(TOR_IP)).isBlocked());
        assertEquals(List.of(), platform.notified);
    }

    @Test
    void aSpentAllowanceLetsPlayersInAndTellsStaffOnce() throws Exception {
        api.route("/" + TOR_IP, request -> new StubApi.Answer(429, "{\"error\":\"quota exceeded\"}", 0));
        enable();
        for (int i = 0; i < 3; i++) {
            assertFalse(engine.login("Steve", UUID.randomUUID(), InetAddress.getByName(TOR_IP)).isBlocked());
        }
        assertEquals(1, platform.notified.stream().filter(n -> n.contains("allowance is spent")).count(),
                platform.notified.toString());
        assertEquals(3, engine.failed.get());
    }

    @Test
    void aReloadReadsTheFilesAgainAndKeepsTheCacheWhenTheKeyIsTheSame() throws Exception {
        api.answer("/" + TOR_IP, Answers.unlimited(TOR_IP, "tor"));
        enable();
        InetAddress address = InetAddress.getByName(TOR_IP);
        assertTrue(engine.login("Steve", UUID.randomUUID(), address).isBlocked());

        Files.writeString(dir.resolve("config.yml"), "checks: {tor: alert}\n");
        assertEquals(List.of(), engine.reload());
        assertEquals(Action.ALERT, engine.login("Steve", UUID.randomUUID(), address).action());
        assertEquals(1, api.count("/" + TOR_IP));

        Files.writeString(dir.resolve("bypass.txt"), "steve\n");
        engine.reload();
        assertTrue(engine.login("Steve", UUID.randomUUID(), address).isBypassed());
    }

    @Test
    void aConfigThatIsNotYamlKeepsTheSettingsInPlaceAndSaysWhy() throws Exception {
        enable();
        Files.writeString(dir.resolve("config.yml"), "checks:\n  tor: allow\n vpn: [\n");
        List<String> problems = engine.reload();
        assertEquals(1, problems.size());
        assertTrue(problems.get(0).startsWith("config.yml can't be read, so the settings stay as they were: line "),
                problems.get(0));
        assertEquals(Action.BLOCK, engine.state().settings().checks.get(Check.TOR));
    }

    @Test
    void aKeyIsCheckedOnStartAndItsPlanNamed() throws Exception {
        api.answer("/api/v1/entitlement", ENTITLEMENT);
        config("api-key: \"id_test_0123456789abcdef\"\n");
        enable();
        String said = waitFor(platform.infos, "Signed in");
        assertEquals("Signed in with id_t...cdef from config.yml: Starter plan, 545 of 250,000 lookups used, "
                + "renewing 2026-12-01.", said);
        assertEquals("Bearer id_test_0123456789abcdef", api.requests.get(0).header("Authorization"));
    }

    @Test
    void aKeyOnNoPaidPlanIsNamedAsTheApiNamesIt() throws Exception {
        api.answer("/api/v1/entitlement", FREE_PLAN_ENTITLEMENT);
        config("api-key: \"id_test_0123456789abcdef\"\n");
        enable();
        assertEquals("Signed in with id_t...cdef from config.yml: Unlimited plan, 45 of 100 lookups with every "
                + "field used in its trial to 2026-12-01.", waitFor(platform.infos, "Signed in"));
    }

    @Test
    void aPlanIsNamedAsTheApiNamesIt() throws Exception {
        api.answer("/api/v1/entitlement", ENTITLEMENT.replace("\"name\":\"Starter\"", "\"name\":\"Data 250K\""));
        config("api-key: \"id_test_0123456789abcdef\"\n");
        enable();
        assertEquals("Signed in with id_t...cdef from config.yml: Data 250K plan, 545 of 250,000 lookups used, "
                + "renewing 2026-12-01.", waitFor(platform.infos, "Signed in"));
    }

    @Test
    void aRefusedKeyIsSaidSoBeforeAnyPlayerJoins() throws Exception {
        api.route("/api/v1/entitlement", request -> new StubApi.Answer(401, "{\"error\":\"invalid api key\"}", 0));
        config("api-key: \"id_test_0123456789abcdef\"\n");
        enable();
        String said = waitFor(platform.warnings, "The API key");
        assertTrue(said.contains("was refused"), said);
    }

    @Test
    void aKeyOutsideAsciiIsSaidSoAndLookupsGoWithoutOne() throws Exception {
        api.answer("/" + TOR_IP, Answers.unlimited(TOR_IP, "tor"));
        enable();
        Files.writeString(dir.resolve("config.yml"), "api-key: \"id_test_0123456789abcdé\"\n");
        assertEquals(List.of("the API key can't be used (the API key holds a character outside ASCII), "
                + "so lookups go without one"), engine.reload());
        assertTrue(engine.login("Steve", UUID.randomUUID(), InetAddress.getByName(TOR_IP)).isBlocked());
        assertNull(api.requests.get(api.requests.size() - 1).header("Authorization"));
    }

    @Test
    void aSavedKeyKeepsEveryOtherLineAndIsUsedAtOnce() throws Exception {
        String ip = "146.70.116.130";
        api.answer("/api/v1/entitlement", ENTITLEMENT);
        api.answer("/" + ip, Answers.trial(Answers.paid(ip), false));
        enable();
        String before = Files.readString(dir.resolve("config.yml"));
        assertTrue(engine.saveApiKey("id_test_0123456789abcdef"));
        String after = Files.readString(dir.resolve("config.yml"));
        assertEquals(before.replace("api-key: \"\"", "api-key: \"id_test_0123456789abcdef\""), after);
        Verdict verdict = engine.login("Steve", UUID.randomUUID(), InetAddress.getByName(ip));
        assertEquals("VPN (Mullvad), hosting (M247), residential proxy (Bright Data)", verdict.reason());
        assertTrue(api.requests.stream().anyMatch(r -> ("/" + ip).equals(r.path())
                && "Bearer id_test_0123456789abcdef".equals(r.header("Authorization"))));
    }

    @Test
    void aProxySendsEachPlayerToTheServerForTheirCountryElseTheirContinent() throws Exception {
        api.answer("/177.1.2.3", Answers.unlimited("177.1.2.3"));
        api.answer("/181.1.2.3", Answers.unlimitedAt("181.1.2.3", Answers.LOCATION_AR, Answers.ASN_ISP));
        api.answer("/146.70.116.130", Answers.unlimitedAt("146.70.116.130", Answers.LOCATION_AT, Answers.ASN_M247));
        api.answer("/45.83.91.1", Answers.unlimited("45.83.91.1", "tor"));
        api.answer("/36.110.1.2", Answers.unlimitedAt("36.110.1.2", Answers.LOCATION_UNKNOWN, Answers.ASN_ISP));
        platform.servers = Optional.of(Set.of("lobby-eu", "lobby-sa", "lobby-br", "lobby-no", "hub"));
        config("checks: {hosting: allow}\n" + ROUTING);
        enable();
        assertEquals(List.of(), platform.warnings);

        assertEquals(Optional.of("lobby-br"), routeOf("Bia", "177.1.2.3"));
        assertEquals(Optional.of("lobby-sa"), routeOf("Ana", "181.1.2.3"));
        assertEquals(Optional.of("lobby-eu"), routeOf("Jan", "146.70.116.130"));
        assertEquals(Optional.empty(), routeOf("Lin", "36.110.1.2"));
        assertEquals(Optional.empty(), routeOf("Bot", "45.83.91.1"));
        assertEquals(3, engine.routed.get());
        assertTrue(platform.infos.get(0).endsWith("; sending BR to lobby-br, NO to lobby-no, EU to lobby-eu, "
                + "SA to lobby-sa"), platform.infos.get(0));

        // Asked once per login, and never for a player who was never looked up.
        UUID once = UUID.randomUUID();
        engine.login("Bia", once, InetAddress.getByName("177.1.2.3"));
        assertEquals(Optional.of("lobby-br"), engine.route(once));
        assertEquals(Optional.empty(), engine.route(once));
        Files.writeString(dir.resolve("bypass.txt"), "177.1.2.0/24\n");
        engine.reload();
        UUID staff = UUID.randomUUID();
        assertTrue(engine.login("Bia", staff, InetAddress.getByName("177.1.2.3")).isBypassed());
        assertEquals(Optional.empty(), engine.route(staff));
    }

    @Test
    void routingToAServerTheProxyLacksIsSaidAndSendsNobodyThere() throws Exception {
        api.answer("/146.70.116.130", Answers.unlimitedAt("146.70.116.130", Answers.LOCATION_AT, Answers.ASN_M247));
        platform.servers = Optional.of(Set.of("hub"));
        config("checks: {hosting: allow}\nrouting: {continents: {EU: lobby-eu}}\n");
        enable();
        assertEquals(List.of("routing sends EU to lobby-eu, but this proxy has no server by that name; it has hub."),
                platform.warnings);
        assertEquals(Optional.empty(), routeOf("Jan", "146.70.116.130"));
    }

    @Test
    void routingOnAServerThatIsNoProxyIsSaidToDoNothing() throws Exception {
        config(ROUTING);
        enable();
        assertEquals(List.of("routing is set, but only a Velocity or BungeeCord proxy sends players to a server, "
                + "so it does nothing here."), platform.warnings);
    }

    private Optional<String> routeOf(String name, String ip) throws Exception {
        UUID uuid = UUID.randomUUID();
        engine.login(name, uuid, InetAddress.getByName(ip));
        return engine.route(uuid);
    }

    private static String waitFor(List<String> lines, String prefix) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            for (String line : lines) {
                if (line.startsWith(prefix)) {
                    return line;
                }
            }
            Thread.sleep(20);
        }
        throw new AssertionError("nothing said starting " + prefix + ": " + lines);
    }
}
