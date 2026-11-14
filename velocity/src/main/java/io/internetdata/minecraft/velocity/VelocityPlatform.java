package io.internetdata.minecraft.velocity;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;

import io.internetdata.minecraft.Platform;

import org.slf4j.Logger;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

final class VelocityPlatform implements Platform {
    private final ProxyServer proxy;
    private final Logger logger;
    private final Path dataDirectory;

    VelocityPlatform(ProxyServer proxy, Logger logger, Path dataDirectory) {
        this.proxy = proxy;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Override
    public String server() {
        // "3.4.0 (git-...)", or "4.3.0-SNAPSHOT (git-...-b44)": the release, without the build.
        String version = proxy.getVersion().getVersion().split("[ -]")[0];
        return proxy.getVersion().getName() + " " + version;
    }

    @Override
    public Path dataDirectory() {
        return dataDirectory;
    }

    @Override
    public void info(String message) {
        logger.info(message);
    }

    @Override
    public void warn(String message) {
        logger.warn(message);
    }

    @Override
    public void notify(String permission, String text) {
        for (Player player : proxy.getAllPlayers()) {
            if (player.hasPermission(permission)) {
                player.sendMessage(VelocitySender.component(text));
            }
        }
    }

    @Override
    public Optional<OnlinePlayer> player(String name) {
        return proxy.getPlayer(name).map(p -> new OnlinePlayer(p.getUsername(), p.getUniqueId(),
                p.getRemoteAddress().getAddress()));
    }

    @Override
    public List<String> playerNames() {
        return proxy.getAllPlayers().stream().map(Player::getUsername).toList();
    }

    @Override
    public Optional<Set<String>> servers() {
        return Optional.of(proxy.getAllServers().stream()
                .map(RegisteredServer::getServerInfo)
                .map(info -> info.getName())
                .collect(Collectors.toUnmodifiableSet()));
    }
}
