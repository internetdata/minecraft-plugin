package io.internetdata.minecraft;

import java.net.InetAddress;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The few things core needs from the server it runs in. Each platform module implements it once.
 *
 * <p>Every method may be called from any thread: a platform that keeps state on one thread, Folia's
 * regions or Bukkit's main thread, hops there itself.
 */
public interface Platform {
    /** The server software and its version, like "Paper 26.3", for the User-Agent and /inetd status. */
    String server();

    /** The folder holding config.yml and bypass.txt, made if it does not exist. */
    Path dataDirectory();

    void info(String message);

    void warn(String message);

    /** Sends a line, already colored, to every online player holding {@code permission}. */
    void notify(String permission, String text);

    /** The online player of this name, ignoring case. */
    Optional<OnlinePlayer> player(String name);

    /** Every online player's name, for tab completion. */
    List<String> playerNames();

    /** The servers behind this proxy, by name, which routing sends players to; empty on a server. */
    Optional<Set<String>> servers();

    /** A player who is online now. */
    record OnlinePlayer(String name, UUID uuid, InetAddress address) {}
}
