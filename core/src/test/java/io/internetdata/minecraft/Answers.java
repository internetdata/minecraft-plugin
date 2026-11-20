package io.internetdata.minecraft;

import java.util.Set;

/** Lookup answers in the shape each plan is served, as the API documents them. */
final class Answers {
    private Answers() {}

    static final String LOCATION_AT = "{\"continent\":\"EU\",\"continent_name\":\"Europe\",\"country\":\"AT\","
            + "\"country_name\":\"Austria\",\"timezone\":\"Europe/Vienna\",\"currency_code\":\"EUR\","
            + "\"currency_name\":\"Euro\",\"currency_symbol\":\"E\",\"flag_emoji\":\"\",\"flag_unicode\":\"\"}";
    static final String LOCATION_BR = "{\"continent\":\"SA\",\"continent_name\":\"South America\",\"country\":\"BR\","
            + "\"country_name\":\"Brazil\",\"timezone\":\"America/Sao_Paulo\",\"currency_code\":\"BRL\","
            + "\"currency_name\":\"Real\",\"currency_symbol\":\"R$\",\"flag_emoji\":\"\",\"flag_unicode\":\"\"}";
    static final String LOCATION_AR = "{\"continent\":\"SA\",\"continent_name\":\"South America\",\"country\":\"AR\","
            + "\"country_name\":\"Argentina\",\"timezone\":\"America/Argentina/Buenos_Aires\",\"currency_code\":\"ARS\","
            + "\"currency_name\":\"Peso\",\"currency_symbol\":\"$\",\"flag_emoji\":\"\",\"flag_unicode\":\"\"}";
    static final String LOCATION_UNKNOWN = "{\"continent\":\"\",\"continent_name\":\"\",\"country\":\"\","
            + "\"country_name\":\"\",\"timezone\":\"\",\"currency_code\":\"\",\"currency_name\":\"\","
            + "\"currency_symbol\":\"\",\"flag_emoji\":\"\",\"flag_unicode\":\"\"}";
    static final String ASN_M247 = "{\"asn\":9009,\"name\":\"M247 Europe SRL\",\"domain\":\"m247global.com\","
            + "\"type\":\"hosting\",\"prefix\":\"146.70.116.0/24\"}";
    static final String ASN_CLOUDFLARE = "{\"asn\":13335,\"name\":\"Cloudflare, Inc.\",\"domain\":\"cloudflare.com\","
            + "\"type\":\"hosting\",\"prefix\":\"104.28.0.0/16\"}";
    static final String ASN_ISP = "{\"asn\":28573,\"name\":\"Claro NXT\",\"domain\":\"claro.com.br\","
            + "\"type\":\"isp\",\"prefix\":\"177.0.0.0/14\"}";
    static final String ASN_STARLINK = "{\"asn\":14593,\"name\":\"SpaceX Services, Inc.\",\"domain\":\"spacex.com\","
            + "\"type\":\"isp\",\"prefix\":\"98.97.0.0/16\"}";
    private static final String TOR = "{\"provider\":\"\",\"provider_name\":\"\",\"confidence\":\"high\","
            + "\"last_seen\":\"2026-11-13\"}";
    private static final String RELAY = "{\"provider\":\"apple\",\"provider_name\":\"iCloud Private Relay\","
            + "\"confidence\":\"high\",\"last_seen\":\"2026-11-13\"}";

    /**
     * Unlimited, with no key or past a free key's daily trial: the flags every plan answers, true for
     * each one named in {@code flags} (tor, relay, anycast, carrier, satellite, ssh), and where the
     * address is and who holds it.
     */
    static String unlimitedAt(String ip, String location, String asn, String... flags) {
        Set<String> on = Set.of(flags);
        return "{\"ip\":\"" + ip + "\",\"is_bogon\":false,\"is_tor\":" + on.contains("tor")
                + ",\"is_relay\":" + on.contains("relay") + ",\"is_anycast\":" + on.contains("anycast")
                + ",\"is_carrier\":" + on.contains("carrier") + ",\"is_satellite\":" + on.contains("satellite")
                + ",\"is_ssh\":" + on.contains("ssh") + ",\"is_smtp\":false,\"is_web\":false"
                + ",\"is_dns_resolver\":false,\"is_bittorrent\":false"
                + ",\"tor\":" + (on.contains("tor") ? TOR : "{}") + ",\"relay\":" + (on.contains("relay") ? RELAY : "{}")
                + ",\"location\":" + location + ",\"asn\":" + asn
                + ",\"company\":{\"name\":\"\",\"domain\":\"\"},\"hostname\":\"\",\"abuse\":{\"email\":\"\"}}";
    }

    /** Unlimited, from a home connection in Brazil. */
    static String unlimited(String ip, String... flags) {
        return unlimitedAt(ip, LOCATION_BR, ASN_ISP, flags);
    }

    /** A key on the free plan: the paid answer while its trial lasts, Unlimited's after. */
    static String trial(String unlimitedOrPaid, boolean expired) {
        return unlimitedOrPaid.substring(0, unlimitedOrPaid.length() - 1) + ",\"trial_expired\":" + expired + "}";
    }

    /** A paid plan: every member, here a Mullvad exit in an M247 range also seen in a residential pool. */
    static String paid(String ip) {
        return "{\"ip\":\"" + ip + "\",\"is_bogon\":false,\"is_vpn\":true,\"is_tor\":false,\"is_relay\":false,"
                + "\"is_resproxy\":true,\"is_dcproxy\":false,\"is_mobproxy\":false,\"is_pubproxy\":false,"
                + "\"is_hosting\":true,\"is_cdn\":false,\"is_anycast\":false,\"is_carrier\":false,\"is_satellite\":false,"
                + "\"is_ssh\":false,\"is_smtp\":false,\"is_web\":false,\"is_dns_resolver\":false,\"is_bittorrent\":false,"
                + "\"vpn\":{\"provider\":\"mullvad\",\"provider_name\":\"Mullvad\",\"confidence\":\"high\","
                + "\"last_seen\":\"2026-11-13\"},\"tor\":{},\"relay\":{},"
                + "\"resproxy\":{\"provider\":\"brightdata\",\"provider_name\":\"Bright Data\",\"first_seen\":\"2026-08-01\","
                + "\"last_seen\":\"2026-11-13\",\"hits\":7,\"hits_days_pct\":42,\"providers_num\":2},"
                + "\"dcproxy\":{},\"mobproxy\":{},\"pubproxy\":{},"
                + "\"hosting\":{\"provider\":\"m247\",\"provider_name\":\"M247\",\"confidence\":\"high\","
                + "\"last_seen\":\"2026-11-13\"},\"cdn\":{},\"carrier\":{},\"satellite\":{},\"ssh\":{},\"smtp\":{},"
                + "\"web\":[],\"dns_resolver\":{},\"bittorrent\":{},"
                + "\"location\":" + LOCATION_AT + ",\"asn\":" + ASN_M247 + ","
                + "\"company\":{\"name\":\"M247 LTD Vienna Infrastructure\",\"domain\":\"m247.ro\",\"type\":\"hosting\","
                + "\"country\":\"AT\"},\"hostname\":\"\",\"abuse\":{\"email\":\"abuse@m247.ro\"}}";
    }

    /** A paid plan: an iCloud Private Relay exit on Cloudflare, which answers is_hosting as well as is_relay. */
    static String paidRelay(String ip) {
        return "{\"ip\":\"" + ip + "\",\"is_bogon\":false,\"is_vpn\":false,\"is_tor\":false,\"is_relay\":true,"
                + "\"is_resproxy\":false,\"is_dcproxy\":false,\"is_mobproxy\":false,\"is_pubproxy\":false,"
                + "\"is_hosting\":true,\"is_cdn\":false,\"is_anycast\":false,\"is_carrier\":false,\"is_satellite\":false,"
                + "\"is_ssh\":false,\"is_smtp\":false,\"is_web\":false,\"is_dns_resolver\":false,\"is_bittorrent\":false,"
                + "\"vpn\":{},\"tor\":{},\"relay\":" + RELAY + ",\"resproxy\":{},\"dcproxy\":{},\"mobproxy\":{},"
                + "\"pubproxy\":{},\"hosting\":{\"provider\":\"cloudflare\",\"provider_name\":\"Cloudflare\","
                + "\"confidence\":\"high\",\"last_seen\":\"2026-11-13\"},\"cdn\":{},\"carrier\":{},\"satellite\":{},"
                + "\"ssh\":{},\"smtp\":{},\"web\":[],\"dns_resolver\":{},\"bittorrent\":{},"
                + "\"location\":" + LOCATION_AT + ",\"asn\":" + ASN_CLOUDFLARE + ","
                + "\"company\":{\"name\":\"Cloudflare, Inc.\",\"domain\":\"cloudflare.com\",\"type\":\"hosting\","
                + "\"country\":\"US\"},\"hostname\":\"\",\"abuse\":{\"email\":\"abuse@cloudflare.com\"}}";
    }

    /**
     * A paid plan, an ordinary connection: every flag false and every detail empty, but those named in
     * {@code details}, each a member and its JSON, whose flag is true.
     */
    static String paidClean(String ip, String location, String asn, String... details) {
        StringBuilder flags = new StringBuilder();
        StringBuilder objects = new StringBuilder();
        for (String member : new String[] {"carrier", "satellite"}) {
            String detail = "{}";
            for (int i = 0; i + 1 < details.length; i += 2) {
                if (details[i].equals(member)) {
                    detail = details[i + 1];
                }
            }
            flags.append(",\"is_").append(member).append("\":").append(!detail.equals("{}"));
            objects.append(",\"").append(member).append("\":").append(detail);
        }
        return "{\"ip\":\"" + ip + "\",\"is_bogon\":false,\"is_vpn\":false,\"is_tor\":false,\"is_relay\":false,"
                + "\"is_resproxy\":false,\"is_dcproxy\":false,\"is_mobproxy\":false,\"is_pubproxy\":false,"
                + "\"is_hosting\":false,\"is_cdn\":false,\"is_anycast\":false" + flags
                + ",\"is_ssh\":false,\"is_smtp\":false,\"is_web\":false,\"is_dns_resolver\":false,\"is_bittorrent\":false,"
                + "\"vpn\":{},\"tor\":{},\"relay\":{},\"resproxy\":{},\"dcproxy\":{},\"mobproxy\":{},\"pubproxy\":{},"
                + "\"hosting\":{},\"cdn\":{}" + objects + ",\"ssh\":{},\"smtp\":{},\"web\":[],\"dns_resolver\":{},"
                + "\"bittorrent\":{},\"location\":" + location + ",\"asn\":" + asn + ","
                + "\"company\":{\"name\":\"\",\"domain\":\"\",\"type\":\"isp\",\"country\":\"\"},\"hostname\":\"\","
                + "\"abuse\":{\"email\":\"\"}}";
    }
}
