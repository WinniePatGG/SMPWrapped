# Changelog

All notable changes to SMPWrapped are documented here.
This file is the release note used for the Modrinth project page.

---

## 0.0.1 — First release

The first public build of SMPWrapped. It replaces a plain stats website with a
real Wrapped experience, and runs on almost any modern server.

### Requirements

| | |
|---|---|
| Minecraft | **1.16.3 → 26.3** |
| Java | **11+** |
| Server software | Paper, Spigot, or a fork of either |

One jar covers the whole range. It is compiled against the 1.16.3 API and Java 11
bytecode, then verified by actually booting it on **Paper 1.16.5 (Java 11)** and
**Paper 1.21.11 (Java 21)**.

Versioning follows `0.0.x` while things settle, then `0.1.0`, then `1.0.0`.

### Fullscreen Wrapped, not a stats website

The old site — home page, leaderboard, stats browser, search, navbar, footer — has
been removed. SMPWrapped is now two things:

1. A **player name entry page**, and
2. A **fullscreen Wrapped story** that ends in a **full dashboard** of every number.

- Spotify-style **fullscreen, slide-by-slide story** with a segmented progress bar
- **Auto-advance** every 7 seconds, with pause/play
- Navigate by **tap, swipe, arrow keys or the on-screen chevrons**
- 11 slides: playtime, when you were most active, how you rank, your archetype,
  achievements and milestones
- Slides straight into a **dashboard of the complete dataset** when it finishes
- **Playtime** and **your most active hours** are called out explicitly
- **Percentile rankings** rendered as *"Top X%"* so they read instantly
- **6 player archetypes** — The Miner, The Explorer, The Builder, The Monster
  Hunter, The Survivor, The Collector

### 62 achievements across 5 tiers and 12 categories

Every achievement is earned automatically and shown on its own card in the story
and dashboard. Categories: Activity, Mining, Building, Combat, Survival,
Exploration, Crafting, Fishing, Collection, Social, Server, Fun.

Highlights include **Night Owl** (mostly online 10pm–5am), **Early Bird**,
**Graveyard Shift** (always around, whatever the hour), **Flawless** (an hour,
zero deaths), **Diamond Hands**, **Dragon Slayer**, **Wanderer**, **Globetrotter**,
**Unbreakable** (7-day login streak) and **Top 1%**.

Tiers run Bronze → Silver → Gold → Diamond → Legendary.

### Automatic weekly and monthly Wrapped

- Configurable day and time for a **weekly** and a **monthly** run
- **Missed runs are caught up** on the next startup instead of being skipped
- Last-run state is persisted, so **restarts never double-generate**
- Online players get a **clickable chat link** when their Wrapped is ready

### Seasons that work end to end

The lifecycle is now real: `create → play → end → publish`.

- `create` makes the new season **current** and ends any previous active one
- **Exactly one season can be `ACTIVE`**, enforced by the database itself
- `switch` genuinely changes which season is being recorded
- `end` can **auto-publish** when `publication.auto-publish-on-end` is on
- Switching seasons **flushes stats to the old season first** and resets
  sampling, so playtime is never attributed to the wrong season
- Works **without a server restart**

### Distance traveled now works

It was never being recorded — there was no movement listener at all.

- Movement is measured per tick, ignoring head turns
- **Sub-block movement accumulates** instead of rounding away to nothing
- Teleports and portal jumps are rejected so they cannot inflate the number
- Unlocks the Explorer archetype, the Wanderer/Globetrotter/Odysseus
  achievements and the distance percentile

### Database updates more often, and far more cheaply

- Autosave interval **120s → 30s** by default, configurable down to **5s**
- **Only players whose stats actually changed are written**, so a lower interval
  costs almost nothing and an idle server does **zero** writes
- Stats are still flushed instantly when a player leaves and on shutdown

### Creative and survival block tracking

Mining and placing are filtered **consistently**, and you choose the rule:

```yaml
tracking:
  ignore-creative: false   # count creative building too (default)
```

## Advanced logging

A complete logging framework replacing scattered log calls.

- Six levels: `TRACE`, `DEBUG`, `INFO`, `WARN`, `ERROR`, `FATAL`, with a
  configurable threshold
- **Every error logs a full stack trace** including the whole `Caused by` chain
- Structured entries: `timestamp LEVEL [module] [thread] message {key=value}`
  carrying player, season, operation, path, IP and duration
- **Rotating log file** at `plugins/SMPWrapped/logs/smpwrapped.log`, gzipped,
  with size limit and archive count
- Written **asynchronously**, so logging never blocks the server thread;
  dropped lines are counted rather than silently lost
- **Repeating errors are collapsed** instead of flooding the console, with a
  `suppressedRepeats` counter so nothing is silently lost
- **Secrets are redacted** — tokens, passwords and `Bearer` headers become
  `***REDACTED***`, and the admin token is never written to the log
- **Access logging** for every HTTP request, with 4xx/5xx severity
- A **shutdown summary** reporting error counts, top recurring errors, log
  volume, rotations and dropped lines

```yaml
logging:
  level: INFO
  console: true
  file:
    enabled: true
    max-size-mb: 5
    keep-files: 5
  stack-traces: true
  dedupe:
    enabled: true
    threshold: 5
```

## Bug fixes

- **Player name lookup always failed.** Players were only written to the database
  on their *very first* login, so anyone returning to an existing server was
  invisible to the dashboard. Players are now registered on every join, and name
  changes are picked up.
- **Error messages were being destroyed.** The HTTP error handlers overwrote every
  `404`/`500` produced by a route, turning `"Player not found"` and
  `"No active season"` into a useless `"Not found"`.
- **Config defaults could disagree with the values actually used.** A key could be
  read with a fallback that overrode its registered default, so the config file and
  the running behaviour contradicted each other. Defaults are now materialised
  before they are read, and a test keeps every default and fallback in sync.
- **Stats could be lost on shutdown** if the database threw during the flush.
- **A corrupt stored snapshot** could crash the API; it is now handled and logged.
- **Rollback failures** during a stats transaction are now reported.
- **Corrupt stat values** could cause an unbounded replay loop; the replay is
  clamped and the bad row logged.
- **Rate limiting was dead code** with a hardcoded limit that ignored your
  configured value, and its buckets were never cleaned up.
- **Admin token comparison** was vulnerable to timing attacks; it is now
  constant-time.
- The rate limiter is now actually applied to the API, with cleanup scheduled.

## Also included

- **16 REST endpoints** covering seasons, players, Wrapped snapshots, stats and
  admin actions, with token auth, rate limiting and CORS
- **SQLite storage** via HikariCP in WAL mode, with timestamped backups
- **Tab completion** for every admin subcommand, including season names
- A printable dashboard summary and share text

## Configuration

Everything below is optional — the plugin ships with working defaults.

```yaml
autosave:
  interval-seconds: 30          # was 120, minimum 5

web:
  snapshot-refresh-seconds: 30  # rebuild a stale Wrapped at most this often, 0 disables

tracking:
  ignore-creative: false        # count creative building

publication:
  auto-publish-on-end: false    # publish a season when it ends

schedule:
  weekly:
    enabled: true
    day: MONDAY
    time: "20:00"
  monthly:
    enabled: true
    day: 1
    time: "20:00"

logging:
  level: INFO
```

## Wrapped pages refresh themselves

A stored Wrapped used to be frozen until the next scheduled run. It is now
compared against live stats on every request and rebuilt when anything changed,
rate-limited so repeated reloads cannot hammer the database.

## Known limitations

- **Java 11 is required.** Servers still on Java 8 cannot load this version.
- **1.16.3 was not tested on a live server** — no 1.16.3 server build exists (it
  was a client-only bugfix release). We compile against the exact 1.16.3 API and
  tested on 1.16.5, the closest runnable equivalent.
- **Percentiles can lag.** A refresh triggers on *your* stats changing. If only
  other players play, your ranking updates on your next refresh.
- `PlayerDeathEvent#getEntity` and `sendMessage(String)` are deprecated on modern
  Paper. They still work, but Paper prints a deprecation notice on new versions.