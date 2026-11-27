import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;

/**
 * The lookup API for the end-to-end run, on the loopback: a fixed answer per address, each in the
 * shape a server with no key gets, and one line per request appended to a log, its path and
 * User-Agent, for run.sh to check.
 *
 *   java StubApi.java <port> <log file>
 */
public final class StubApi {
    private static final String LOCATION = "{\"continent\":\"%s\",\"continent_name\":\"%s\",\"country\":\"%s\","
            + "\"country_name\":\"%s\",\"timezone\":\"\",\"currency_code\":\"\",\"currency_name\":\"\","
            + "\"currency_symbol\":\"\",\"flag_emoji\":\"\",\"flag_unicode\":\"\"}";
    private static final String ASN = "{\"asn\":%d,\"name\":\"%s\",\"domain\":\"\",\"type\":\"%s\",\"prefix\":\"%s\","
            + "\"country\":\"\",\"rpki_status\":\"valid\"}";
    private static final String TOR = "{\"provider\":\"\",\"provider_name\":\"\",\"confidence\":\"high\","
            + "\"last_seen\":\"2026-11-13\"}";

    private static final Map<String, String> ANSWERS = Map.of(
            // A Tor exit.
            "/45.83.91.1", answer("45.83.91.1", "tor", String.format(LOCATION, "EU", "Europe", "DE", "Germany"),
                    String.format(ASN, 208323, "Foundation for Applied Privacy", "hosting", "45.83.91.0/24")),
            // A home connection in Brazil, which no routing names.
            "/177.1.2.3", answer("177.1.2.3", "", String.format(LOCATION, "SA", "South America", "BR", "Brazil"),
                    String.format(ASN, 28573, "Claro NXT", "isp", "177.0.0.0/14")),
            // A home connection in Germany, which routing sends to the eu server.
            "/84.128.1.2", answer("84.128.1.2", "", String.format(LOCATION, "EU", "Europe", "DE", "Germany"),
                    String.format(ASN, 3320, "Deutsche Telekom AG", "isp", "84.128.0.0/10")),
            // Starlink in the United States: flagged to staff, and sent to the na server.
            "/98.97.1.2", answer("98.97.1.2", "anycast",
                    String.format(LOCATION, "NA", "North America", "US", "United States"),
                    String.format(ASN, 14593, "SpaceX Services, Inc.", "isp", "98.97.0.0/16")),
            // A home connection in a country the run's config.yml refuses.
            "/36.110.1.2", answer("36.110.1.2", "", String.format(LOCATION, "AS", "Asia", "CN", "China"),
                    String.format(ASN, 4847, "China Networks Inter-Exchange", "isp", "36.110.0.0/16")),
            // A Tor exit that bypass.txt lists, so it must never be asked about.
            "/8.8.4.4", answer("8.8.4.4", "tor",
                    String.format(LOCATION, "NA", "North America", "US", "United States"),
                    String.format(ASN, 15169, "Google LLC", "hosting", "8.8.4.0/24")));

    public static void main(String[] args) throws IOException {
        int port = Integer.parseInt(args[0]);
        Path log = Path.of(args[1]);
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            String agent = exchange.getRequestHeaders().getFirst("User-Agent");
            synchronized (StubApi.class) {
                Files.writeString(log, path + "\t" + agent + "\n", StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            }
            String answer = ANSWERS.get(path);
            byte[] body = (answer != null ? answer : "{\"error\":\"not a valid IP address\"}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(answer != null ? 200 : 400, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
        System.out.println("listening on " + port);
    }

    // An answer with no key: the flags every plan carries, the one named true. The bot, carrier,
    // satellite and service flags are a paid answer's, so it has none of them.
    private static String answer(String ip, String flag, String location, String asn) {
        return "{\"ip\":\"" + ip + "\",\"is_bogon\":false,\"is_tor\":" + flag.equals("tor")
                + ",\"is_relay\":false,\"is_anycast\":" + flag.equals("anycast") + ","
                + "\"tor\":" + (flag.equals("tor") ? TOR : "{}") + ",\"relay\":{},\"location\":" + location
                + ",\"asn\":" + asn + ",\"company\":{\"name\":\"\",\"domain\":\"\"},\"hostname\":\"\","
                + "\"abuse\":{\"email\":\"\"}}";
    }
}
