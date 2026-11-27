# [<img src="https://s3.internetdata.io/internetdata-public/brand/mark.svg" alt="InternetData" height="28"/>](https://internetdata.io/) InternetData Minecraft Plugin

[![version](https://img.shields.io/github/v/release/internetdata/minecraft-plugin)](CHANGELOG.md)
[![license](https://img.shields.io/github/license/internetdata/minecraft-plugin)](LICENSE)

The official [InternetData](https://internetdata.io) plugin for Minecraft servers.

It checks each player's address as they join, refuses or flags to your staff the ones from the countries and networks you choose, and on a Velocity or BungeeCord network sends each player to the server you set for where they are. One jar runs on Paper, Spigot, Folia, Velocity and BungeeCord.

## Getting Started

Download `InternetData-<version>.jar` from the [latest release](https://github.com/internetdata/minecraft-plugin/releases/latest) and put it in your server's `plugins` folder, then restart. On a network, install it on the proxy alone: Velocity or BungeeCord sees each player once, before any server behind it does.

The plugin works without an account on Unlimited, InternetData's free plan: countries, routing, Tor, privacy relays, anycast and hosting networks. VPNs, proxies, mobile networks and satellites need a paid plan, and a server signed in to a free account tries them. To sign in, run this from the console:

```console
/inetd login
```

It prints a link and a code. Open the link, sign in or create a free account, and pick an API key: the plugin saves the key in `config.yml` and starts using it at once. To paste a key instead, put it in `config.yml`'s `api-key` and run `/inetd reload`, or set the `INTERNETDATA_API_KEY` environment variable.

It runs on Java 17 or newer: Paper, Spigot, Purpur, Folia and other Bukkit servers, Velocity 3.4 and later, and BungeeCord and its forks.

## What it checks

`config.yml` sets what happens to a player whose address is each of these: `block` refuses the login with your kick message, `alert` lets them in and tells staff who hold `internetdata.alerts`, and `allow` does nothing.

| Check | Matches | Needs | Default |
|---|---|---|---|
| `vpn` | Commercial VPN servers | a paid plan | `block` |
| `tor` | Tor relays | nothing | `block` |
| `hosting` | Datacenters and cloud providers | nothing | `block` |
| `proxy` | Residential, datacenter, mobile and public proxies | a paid plan | `block` |
| `relay` | Privacy relays and company web gateways, like iCloud Private Relay | nothing | `alert` |
| `carrier` | Mobile networks, whose addresses many players share | a paid plan | `alert` |
| `satellite` | Satellite internet, like Starlink | a paid plan | `alert` |
| `anycast` | Anycast addresses, which many places announce at once | nothing | `alert` |

Without a paid plan, `hosting` goes by the kind of network announcing the address; a paid plan answers it for each address and names the VPN, hosting or proxy provider in your messages. A privacy relay is judged by `relay` alone, even where it runs on a hosting network, so iCloud Private Relay and WARP users are flagged to staff rather than refused. A check your plan doesn't answer is skipped, and the console says so once. The [pricing page](https://internetdata.io/pricing) says what each plan answers.

Countries need no key. List the two-letter codes to refuse in `countries.block`, or the only ones to let in in `countries.allow`:

```yaml
countries:
  block: [CN, RU]
  allow: []
```

A player whose country can't be placed is let in.

## Sending players to the nearest server

On a Velocity or BungeeCord network, `routing` picks the server each player is sent to first, by where they connect from: their country's entry, else their continent's. It needs no key.

```yaml
routing:
  continents:
    EU: lobby-eu
    NA: lobby-na
    AS: lobby-asia
  countries:
    BR: lobby-br
```

Continents are `AF`, `AN`, `AS`, `EU`, `NA`, `OC` and `SA`, and each server is a name from the proxy's own config. A player routing doesn't name, one whose address can't be placed and one in `bypass.txt` go where the proxy sends everyone, and a lobby or reconnect plugin that picks a server itself has the last word.

### Rules

For anything the checks don't cover, a rule blocks or alerts on any field of the answer. A match is written in the shape of the answer and holds when every field it names does. A list means any of them, and `{gte: 5}` compares a number:

```yaml
rules:
  - action: block
    reason: M247
    match:
      asn:
        asn: 9009
  - action: alert
    reason: an SSH server
    match:
      is_ssh: true
  - action: block
    reason: NordVPN
    match:
      vpn:
        provider: nordvpn
```

The fields are those of a [lookup](https://docs.internetdata.io/api/lookup/lookupIp), under the same names. A field set to `false` is ignored, so a rule says what to act on, never what to let through. A rule naming a field your plan doesn't answer, like `vpn` without a paid plan, never matches, and the console says so once.

## Commands

Every command needs `internetdata.admin`. `/inetd` is short for `/internetdata`. Velocity has no operators, so there a permissions plugin such as LuckPerms grants both permissions.

| Command | Does |
|---|---|
| `/inetd check <player\|address>` | Looks one up and says what a login from it would get, and on a proxy where routing sends it |
| `/inetd login` | Signs in and saves an API key |
| `/inetd status` | Your plan and what's left of it, and what's been checked since the server started |
| `/inetd bypass add\|remove <player\|address\|range>` | Never checks a player or an address, or does again |
| `/inetd bypass list` | Lists everything never checked |
| `/inetd reload` | Reads `config.yml` and `bypass.txt` again |

Players and addresses that are never checked live in `bypass.txt`, beside `config.yml`, one per line: a player's name or UUID, an address, or a range like `203.0.113.0/24`. `/inetd bypass add` on an online player stores their UUID, which outlasts a change of name.

## Messages

`messages.kick` is shown to a player refused by a check or a rule, and `messages.kick-country` to one refused by country. Staff with `internetdata.alerts` see `messages.alert` and `messages.blocked`, which the console logs too, at most five lines every ten seconds so a bot flood can't bury your chat. Use `&` color codes and `&#rrggbb` for any color. `{player}`, `{ip}`, `{country}` and `{reason}` are filled in, the last with what matched, like `Tor` or `satellite (Starlink)`.

## When a lookup fails

If our API can't be reached or your allowance is spent, the player is let in, so an outage of ours never locks out your players. Set `fail-closed: true` to refuse them instead. A login waits at most 2.5 seconds for its lookup. On a proxy the lookup starts as the player connects and runs while they sign in, so a login rarely waits on it at all.

## What it sends

For each player who joins, the plugin sends their address to `api.internetdata.io` and nothing else about them: no name, no UUID. Each answer is kept in memory for `cache-minutes`, an hour by default, so a player who rejoins costs no second lookup, and players joining together from one address share one. Requests name the plugin, its version and the server software in their `User-Agent`. With a key, each lookup counts against that key's plan as any API call does. Our privacy policy is at https://internetdata.io/privacy, and questions go to support@internetdata.io.

## Building

```console
./scripts/mvn.sh verify
```

builds and tests every module in Docker, and writes the plugin to `dist/target/InternetData-<version>.jar`. `./e2e/run.sh` boots real Paper, Folia, Velocity and BungeeCord servers with it, joins each from addresses a stub of the API answers for, and on the proxies checks which server each player was sent to.

## Other Libraries

There are official InternetData client libraries available for many languages including PHP, Python, Go, Java, and Ruby. See our GitHub at https://github.com/internetdata for more.

## About InternetData

Geolocation, anonymity, ownership, network, service and domain data for any IP address, AS or domain, by API or as databases you download.

[<picture><source media="(prefers-color-scheme: dark)" srcset="https://s3.internetdata.io/internetdata-public/brand/logo-dark.svg"><img src="https://s3.internetdata.io/internetdata-public/brand/logo.svg" alt="InternetData" height="40"/></picture>](https://internetdata.io/)

## License

This project is licensed under the [MIT License](LICENSE).
