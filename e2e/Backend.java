import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Servers behind the proxy for the end-to-end run, which only say who reached them: each takes a
 * login, appends "<server> <player>" to a log, and turns the player away with "backend <server>".
 *
 *   java Backend.java <log file> <name>:<port>...
 *
 * The proxy sends a player here once the plugin has let them in, so the log's first line for a
 * player names the server routing picked, whatever the proxy tries after the refusal.
 */
public final class Backend {
    public static void main(String[] args) throws IOException {
        Path log = Path.of(args[0]);
        for (int i = 1; i < args.length; i++) {
            String[] spec = args[i].split(":");
            String name = spec[0];
            ServerSocket listener = new ServerSocket();
            listener.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), Integer.parseInt(spec[1])));
            Thread thread = new Thread(() -> serve(listener, name, log), "backend-" + name);
            thread.start();
        }
        System.out.println("listening");
    }

    private static void serve(ServerSocket listener, String name, Path log) {
        while (true) {
            try (Socket socket = listener.accept()) {
                socket.setSoTimeout(10_000);
                answer(socket, name, log);
            } catch (IOException e) {
                // One connection's trouble is its own; the next is served all the same.
            }
        }
    }

    private static void answer(Socket socket, String name, Path log) throws IOException {
        DataInputStream in = new DataInputStream(socket.getInputStream());
        OutputStream out = socket.getOutputStream();
        DataInputStream handshake = packet(in);
        readVarInt(handshake);
        int protocol = readVarInt(handshake);
        readString(handshake);
        handshake.readUnsignedShort();
        int next = readVarInt(handshake);
        if (next == 1) {
            packet(in);
            String status = "{\"version\":{\"name\":\"backend\",\"protocol\":" + protocol + "},"
                    + "\"players\":{\"max\":1,\"online\":0},\"description\":{\"text\":\"" + name + "\"}}";
            out.write(frame(0x00, string(status)));
            DataInputStream ping = packet(in);
            readVarInt(ping);
            out.write(frame(0x01, ping.readAllBytes()));
            return;
        }
        DataInputStream start = packet(in);
        readVarInt(start);
        String player = readString(start);
        synchronized (Backend.class) {
            Files.writeString(log, name + " " + player + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
        }
        out.write(frame(0x00, string("{\"text\":\"backend " + name + "\"}")));
        out.flush();
    }

    private static DataInputStream packet(DataInputStream in) throws IOException {
        byte[] body = new byte[readVarInt(in)];
        in.readFully(body);
        return new DataInputStream(new java.io.ByteArrayInputStream(body));
    }

    private static byte[] frame(int id, byte[] body) throws IOException {
        ByteArrayOutputStream packet = new ByteArrayOutputStream();
        packet.write(varInt(id));
        packet.write(body);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(varInt(packet.size()));
        packet.writeTo(out);
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
}
