# Changelog

What each release changed for you, newest first. Each line is a commit's summary, linked to its full description and diff.

## 1.0.8 - 2026-11-28

### Fixes

- Check hosting on a paid plan or a free key's full answers ([`aacaea2`](https://github.com/internetdata/minecraft-plugin/commit/aacaea2f8cd55afd8df79f8a42d36aa6b81ae31a))
  - Hosting networks are checked on a paid plan or a free key's full answers, with a warning otherwise. With no key the plugin no longer guesses hosting from the type of network, and `/inetd check` says hosting isn't in the plan.

## 1.0.7 - 2026-11-27

### Fixes

- /inetd status words the trial as a window, not a month ([`000b1a2`](https://github.com/internetdata/minecraft-plugin/commit/000b1a26e023e1309f5977dc20d09f19b8cf41ad))
  - On the free plan, `/inetd status` and the line the plugin prints once signed in read "used this month, resetting" a date. They now say how many lookups with every field the trial has used, and the day it ends.

## 1.0.6 - 2026-11-27

### Fixes

- Take SDK 3.0.0, and check carriers and satellites on a paid plan ([`b2cd320`](https://github.com/internetdata/minecraft-plugin/commit/b2cd320d1322be5355010b6d5cde5ff613992b86))
  - Without a key, every lookup had failed since 2026-11-26, letting players in unchecked. Carriers and satellites now need a paid plan or a signed-in server's trial.

## 1.0.5 - 2026-11-22

### Fixes

- Count a free key's trial by the month, with the day it resets ([`aed2dd2`](https://github.com/internetdata/minecraft-plugin/commit/aed2dd2d1a432742d9936cd27a62a0efc7e4db8c))

## 1.0.4 - 2026-11-20

### Fixes

- Say when the overage cap holds checks back ([`755b5c5`](https://github.com/internetdata/minecraft-plugin/commit/755b5c5f6407bfec1f16789c82ed1d0ad6844c19))

## 1.0.3 - 2026-11-20

### Fixes

- Messages, config and store text: the free trial without a count ([`30f90d0`](https://github.com/internetdata/minecraft-plugin/commit/30f90d024d16ae9080bf4b7d9f545eeee3881cbf))

## 1.0.2 - 2026-11-17

### Fixes

- Show the plan's name as the account serves it ([`1f2ff79`](https://github.com/internetdata/minecraft-plugin/commit/1f2ff794814717d415155a36b088d7e9fbaf0f9f))

## 1.0.1 - 2026-11-14

### Fixes

- Judge a privacy relay by relay alone, even on a hosting network ([`b6cdcc5`](https://github.com/internetdata/minecraft-plugin/commit/b6cdcc57d04f59aa3d2b38bc632b45217a138472))

## 1.0.0 - 2026-11-14

### Features

- The InternetData plugin for Minecraft servers: Paper, Spigot, Folia, Velocity and BungeeCord ([`ff43f86`](https://github.com/internetdata/minecraft-plugin/commit/ff43f862f7c8ee8a1c5f68a6bedd86602b6d1582))
