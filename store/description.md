**Choose who joins from where, and send each player to their nearest server.** InternetData checks each player's address as they join, refuses or flags to your staff the ones from countries and networks you don't want, and on a Velocity or BungeeCord network sends everyone else to the server you set for their continent or country.

## 🎯 What it does

- Refuses players from countries you don't want, or from any but the ones you list
- Keeps Tor and bots from rented servers off your server
- Tells your staff in chat, with the player's country, when someone joins over a privacy relay, a mobile network, satellite internet or an anycast address
- Sends each player to the server for their continent or country, on a Velocity or BungeeCord network

## ⚡ Up in a minute

1. Put the jar in your server's `plugins` folder and restart
2. Tor and hosting networks are blocked from the first join, with no account
3. On a network, install it on the proxy alone: Velocity or BungeeCord sees each player once

## 🔧 You decide what happens

- Block, alert your staff or allow, separately for VPNs, Tor, hosting networks, proxies, privacy relays, mobile networks, satellites and anycast
- Rules on any field of the answer, like one network or one country
- Your own kick and alert messages, with color codes
- A bypass list for players, addresses and ranges
- `/inetd check` looks up any player or address on the spot

## 🎁 Free to start

- No account: countries, routing, Tor, hosting networks, privacy relays, mobile networks, satellites and anycast, on every join
- Free account, signed in with `/inetd login`: try VPNs and proxies too
- Paid plan: every check on every join, with the provider named in your messages

## 🚀 Built for busy servers

- One jar for Paper, Spigot, Purpur, Folia, Velocity and BungeeCord, on Java 17 or newer
- A login waits at most 2.5 seconds, and on a proxy the lookup runs while the player signs in
- If our API can't be reached, players are let in, unless you set it to refuse them
- Each answer is kept for an hour, so a player who rejoins costs no second lookup

## 🔒 What it sends

For each player who joins, the plugin sends their address to `api.internetdata.io` and nothing else about them: no name, no UUID. Requests name the plugin, its version and your server software. Privacy policy: https://internetdata.io/privacy

## 💬 Setup and help

Every setting, command and rule: https://docs.internetdata.io/integrations/minecraft. The source: https://github.com/internetdata/minecraft-plugin. Questions: support@internetdata.io
