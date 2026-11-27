package io.internetdata.minecraft;

import io.internetdata.Result;
import io.internetdata.model.CarrierDetail;
import io.internetdata.model.ClassDetail;
import io.internetdata.model.ProxyDetail;
import io.internetdata.model.SatelliteDetail;

import java.util.Locale;
import java.util.Optional;

/**
 * The kinds of address config.yml's checks act on.
 *
 * <p>Tor, relays, mobile networks, satellites, anycast and the type of the network announcing an
 * address are on every answer, with a key or without one. VPNs, proxies and hosting by address are a
 * paid plan's, or a free account's through its trial: the SDK answers those as an empty
 * Optional when the plan leaves them out, and {@link #served} says so, so a check that never could
 * match is reported once rather than silently never firing.
 */
public enum Check {
    VPN {
        @Override
        boolean served(Result result) {
            return result.isVpn().isPresent();
        }

        @Override
        Optional<String> match(Result result) {
            if (!result.isVpn().orElse(false)) {
                return Optional.empty();
            }
            return Optional.of(named("VPN", result.vpn().map(Check::provider)));
        }
    },
    TOR {
        @Override
        boolean served(Result result) {
            return true;
        }

        @Override
        Optional<String> match(Result result) {
            return result.isTor() ? Optional.of("Tor") : Optional.empty();
        }
    },
    HOSTING {
        // Without a key the answer carries no hosting flag, but does say what kind of network
        // announces the address, which tells a datacenter from an ISP well enough to keep bots out.
        // The flag, which a paid answer carries, is per address and wins. A privacy relay is judged by
        // the relay check alone: iCloud Private Relay, WARP and the company gateways run on hosting
        // networks, and most of their addresses answer is_hosting too.
        @Override
        boolean served(Result result) {
            return true;
        }

        @Override
        Optional<String> match(Result result) {
            if (result.isRelay()) {
                return Optional.empty();
            }
            if (result.isHosting().isPresent()) {
                if (!result.isHosting().get()) {
                    return Optional.empty();
                }
                return Optional.of(named("hosting", result.hosting().map(Check::provider)));
            }
            if (result.asn() == null || !"hosting".equalsIgnoreCase(result.asn().getType())) {
                return Optional.empty();
            }
            return Optional.of(named("hosting network", network(result)));
        }
    },
    PROXY {
        @Override
        boolean served(Result result) {
            return result.isResproxy().isPresent() || result.isDcproxy().isPresent()
                    || result.isMobproxy().isPresent() || result.isPubproxy().isPresent();
        }

        @Override
        Optional<String> match(Result result) {
            if (result.isResproxy().orElse(false)) {
                return Optional.of(named("residential proxy", result.resproxy().map(Check::provider)));
            }
            if (result.isDcproxy().orElse(false)) {
                return Optional.of(named("datacenter proxy", result.dcproxy().map(Check::provider)));
            }
            if (result.isMobproxy().orElse(false)) {
                return Optional.of(named("mobile proxy", result.mobproxy().map(Check::provider)));
            }
            return result.isPubproxy().orElse(false) ? Optional.of("public proxy") : Optional.empty();
        }
    },
    RELAY {
        @Override
        boolean served(Result result) {
            return true;
        }

        @Override
        Optional<String> match(Result result) {
            if (!result.isRelay()) {
                return Optional.empty();
            }
            return Optional.of(named("privacy relay", Optional.ofNullable(result.relay()).map(Check::provider)));
        }
    },
    CARRIER {
        // On a paid plan or the trial. The SDK reads an absent flag as false, so the raw answer tells
        // whether the plan carries it. The carrier's own name can be missing; the network announcing
        // the address names it well enough then.
        @Override
        boolean served(Result result) {
            return result.raw().getIsCarrier() != null;
        }

        @Override
        Optional<String> match(Result result) {
            if (!result.isCarrier()) {
                return Optional.empty();
            }
            Optional<String> name = result.carrier().map(CarrierDetail::getName).filter(n -> !n.isBlank());
            return Optional.of(named("mobile network", name.isPresent() ? name : network(result)));
        }
    },
    SATELLITE {
        // On a paid plan or the trial, told apart as CARRIER's is.
        @Override
        boolean served(Result result) {
            return result.raw().getIsSatellite() != null;
        }

        @Override
        Optional<String> match(Result result) {
            if (!result.isSatellite()) {
                return Optional.empty();
            }
            Optional<String> name = result.satellite().map(Check::provider).filter(n -> !n.isBlank());
            return Optional.of(named("satellite", name.isPresent() ? name : network(result)));
        }
    },
    ANYCAST {
        @Override
        boolean served(Result result) {
            return true;
        }

        @Override
        Optional<String> match(Result result) {
            return result.isAnycast() ? Optional.of(named("anycast", network(result))) : Optional.empty();
        }
    };

    /** The spelling config.yml uses. */
    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** The check config.yml calls {@code key}, or null. */
    static Check of(String key) {
        for (Check check : values()) {
            if (check.key().equals(key)) {
                return check;
            }
        }
        return null;
    }

    /** Whether the answer carries what this check reads. False means the plan does not serve it. */
    abstract boolean served(Result result);

    /** Why this check matches the answer, or empty when it does not. */
    abstract Optional<String> match(Result result);

    // "VPN (NordVPN)", or "VPN" where nobody is named: an unattributed range, or a plan that serves
    // the flag without its detail.
    private static String named(String what, Optional<String> who) {
        return who.filter(name -> !name.isBlank()).map(name -> what + " (" + name + ")").orElse(what);
    }

    // A provider's name as people know it, which an answer carries beside its ID.
    private static String provider(ClassDetail detail) {
        return preferred(detail.getProviderName(), detail.getProvider());
    }

    private static String provider(ProxyDetail detail) {
        return preferred(detail.getProviderName(), detail.getProvider());
    }

    private static String provider(SatelliteDetail detail) {
        return preferred(detail.getProviderName(), detail.getProvider());
    }

    private static String preferred(String name, String id) {
        return name != null && !name.isBlank() ? name : id;
    }

    private static Optional<String> network(Result result) {
        return Optional.ofNullable(result.asn()).map(asn -> asn.getName());
    }
}
