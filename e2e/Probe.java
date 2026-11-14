import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.Inflater;

/**
 * Joins a server as an offline-mode player from any address, and says how the login ended.
 *
 *   java Probe.java <host> <port> <name> <source address> [follow | watch <word>... <seconds>]
 *   java Probe.java uuid <name>
 *
 * The connection opens with a PROXY protocol header naming the source address, so a server that
 * trusts the header (Paper's proxies.proxy-protocol, Velocity's haproxy-protocol, BungeeCord's
 * proxy_protocol) sees the player arriving from it. Prints one line: SUCCESS, DISCONNECT and the
 * reason's JSON, or ERROR and why.
 *
 * follow stays after a SUCCESS until the server closes the connection, acknowledging the login
 * where the protocol asks for it, so a proxy goes on to send the player to a server behind it.
 * watch stays as a player for up to that many seconds, on 1.20.1 and older, where the login ends in
 * play, and prints SAW once one packet holds every word, as a chat line staff are sent does, or
 * MISSED. uuid prints the UUID an offline-mode server gives a name, for ops.json.
 */
public final class Probe {
    private static final Pattern PROTOCOL = Pattern.compile("\"protocol\"\\s*:\\s*(-?\\d+)");

    public static void main(String[] args) throws Exception {
        if (args[0].equals("uuid")) {
            System.out.println(offline(args[1]));
            return;
        }
        String host = args[0];
        int port = Integer.parseInt(args[1]);
        String name = args[2];
        String source = args[3];
        String mode = args.length > 4 ? args[4] : "";
        List<String> words = args.length > 6 ? List.of(args).subList(5, args.length - 1) : List.of();
        int seconds = args.length > 6 ? Integer.parseInt(args[args.length - 1]) : 15;
        try {
            int protocol = protocol(host, port);
            System.out.println(login(host, port, name, source, protocol, mode, words, seconds));
        } catch (IOException e) {
            System.out.println("ERROR " + e);
        }
    }

    private static UUID offline(String name) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
    }

    // A status ping, whose answer names the protocol to log in with: a server's own, or for a proxy
    // the one asked with, when it speaks it.
    private static int protocol(String host, int port) throws IOException {
        try (Socket socket = connect(host, port, "10.255.0.1")) {
            OutputStream out = socket.getOutputStream();
            out.write(packet(0x00, varInt(767), string(host), u16(port), varInt(1)));
            out.write(packet(0x00));
            DataInputStream in = new DataInputStream(socket.getInputStream());
            byte[] body = readPacket(in, -1);
            String json = readString(new DataInputStream(new java.io.ByteArrayInputStream(body, 1, body.length - 1)));
            Matcher m = PROTOCOL.matcher(json);
            if (!m.find()) {
                throw new IOException("no protocol in the status answer: " + json);
            }
            return Integer.parseInt(m.group(1));
        }
    }

    private static String login(String host, int port, String name, String source, int protocol, String mode,
            List<String> words, int seconds) throws IOException {
        try (Socket socket = connect(host, port, source)) {
            OutputStream out = socket.getOutputStream();
            out.write(packet(0x00, varInt(protocol), string(host), u16(port), varInt(2)));
            UUID uuid = offline(name);
            if (protocol >= 764) {
                out.write(packet(0x00, string(name), uuid(uuid)));
            } else if (protocol >= 761) {
                out.write(packet(0x00, string(name), new byte[] {1}, uuid(uuid)));
            } else if (protocol >= 759) {
                out.write(packet(0x00, string(name), new byte[] {0}, new byte[] {0}));
            } else {
                out.write(packet(0x00, string(name)));
            }
            DataInputStream in = new DataInputStream(socket.getInputStream());
            int threshold = -1;
            while (true) {
                byte[] body = readPacket(in, threshold);
                DataInputStream packet = new DataInputStream(new java.io.ByteArrayInputStream(body));
                int id = readVarInt(packet);
                switch (id) {
                    case 0x00 -> {
                        return "DISCONNECT " + readString(packet);
                    }
                    case 0x02 -> {
                        if (mode.equals("follow")) {
                            follow(socket, in, out, protocol, threshold);
                        } else if (mode.equals("watch")) {
                            return watch(socket, in, threshold, words, seconds);
                        }
                        return "SUCCESS";
                    }
                    case 0x03 -> threshold = readVarInt(packet);
                    case 0x01 -> {
                        return "ERROR the server asked for encryption, so it is in online mode";
                    }
                    default -> {
                        // A login plugin message or anything else before the outcome: keep reading.
                    }
                }
            }
        }
    }

    // From 1.20.2 the client acknowledges the login before a proxy picks a server for it.
    private static void follow(Socket socket, DataInputStream in, OutputStream out, int protocol, int threshold)
            throws IOException {
        if (protocol >= 764) {
            out.write(threshold < 0 ? packet(0x03) : compressedFrame(packet(0x03)));
        }
        socket.setSoTimeout(15_000);
        try {
            while (true) {
                readPacket(in, threshold);
            }
        } catch (IOException closed) {
            // The proxy closed it, having sent the player on, or the wait ran out.
        }
    }

    private static String watch(Socket socket, DataInputStream in, int threshold, List<String> words, int seconds)
            throws IOException {
        long deadline = System.nanoTime() + seconds * 1_000_000_000L;
        socket.setSoTimeout(1_000);
        while (System.nanoTime() < deadline) {
            byte[] body;
            try {
                body = readPacket(in, threshold);
            } catch (java.net.SocketTimeoutException quiet) {
                continue;
            }
            String text = new String(body, StandardCharsets.UTF_8);
            if (words.stream().allMatch(text::contains)) {
                return "SAW";
            }
        }
        return "MISSED";
    }

    // A packet under the compression threshold: its length, a zero data length, and the packet.
    private static byte[] compressedFrame(byte[] framed) throws IOException {
        DataInputStream in = new DataInputStream(new java.io.ByteArrayInputStream(framed));
        byte[] body = new byte[readVarInt(in)];
        in.readFully(body);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(varInt(body.length + 1));
        out.write(0);
        out.write(body);
        return out.toByteArray();
    }

    private static Socket connect(String host, int port, String source) throws IOException {
        Socket socket = new Socket();
        socket.setSoTimeout(30_000);
        socket.connect(new InetSocketAddress(host, port), 10_000);
        boolean v6 = source.contains(":");
        String header = "PROXY " + (v6 ? "TCP6 " : "TCP4 ") + source + " " + (v6 ? "::1" : "127.0.0.1") + " 51234 "
                + port + "\r\n";
        socket.getOutputStream().write(header.getBytes(StandardCharsets.US_ASCII));
        return socket;
    }

    private static byte[] readPacket(DataInputStream in, int threshold) throws IOException {
        int length = readVarInt(in);
        byte[] frame = new byte[length];
        in.readFully(frame);
        if (threshold < 0) {
            return frame;
        }
        DataInputStream framed = new DataInputStream(new java.io.ByteArrayInputStream(frame));
        int dataLength = readVarInt(framed);
        byte[] rest = framed.readAllBytes();
        if (dataLength == 0) {
            return rest;
        }
        Inflater inflater = new Inflater();
        inflater.setInput(rest);
        byte[] out = new byte[dataLength];
        try {
            inflater.inflate(out);
        } catch (java.util.zip.DataFormatException e) {
            throw new IOException(e);
        } finally {
            inflater.end();
        }
        return out;
    }

    private static byte[] packet(int id, byte[]... fields) throws IOException {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(varInt(id));
        for (byte[] field : fields) {
            body.write(field);
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(varInt(body.size()));
        body.writeTo(out);
        return out.toByteArray();
    }

    private static byte[] varInt(int value) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        do {
            int b = value & 0x7f;
            value >>>= 7;
            out.write(value != 0 ? b | 0x80 : b);
        } while (value != 0);
        return out.toByteArray();
    }

    private static int readVarInt(InputStream in) throws IOException {
        int value = 0;
        for (int shift = 0; shift < 35; shift += 7) {
            int b = in.read();
            if (b < 0) {
                throw new EOFException("the connection closed");
            }
            value |= (b & 0x7f) << shift;
            if ((b & 0x80) == 0) {
                return value;
            }
        }
        throw new IOException("a VarInt longer than five bytes");
    }

    private static byte[] string(String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(varInt(bytes.length));
        out.write(bytes);
        return out.toByteArray();
    }

    private static String readString(DataInputStream in) throws IOException {
        byte[] bytes = new byte[readVarInt(in)];
        in.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static byte[] u16(int value) {
        return new byte[] {(byte) (value >> 8), (byte) value};
    }

    private static byte[] uuid(UUID uuid) {
        byte[] out = new byte[16];
        long[] halves = {uuid.getMostSignificantBits(), uuid.getLeastSignificantBits()};
        for (int i = 0; i < 16; i++) {
            out[i] = (byte) (halves[i / 8] >> (8 * (7 - i % 8)));
        }
        return out;
    }
}
