# SMPWrapped Troubleshooting

## Build Issues

### `Could not resolve xyz.jpenilla:run-task`

The upstream `run-paper` Gradle plugin is only compatible with Gradle 9.7+. SMPWrapped
uses a self-contained `runServer` task instead, so this error should not occur. If you
see it, make sure you removed any `xyz.jpenilla.run-paper` plugin declaration from
`build.gradle.kts` (the shipped file does not include it).

### `Unsupported class file major version` / wrong Java version

SMPWrapped requires **Java 11 or newer**. Verify:

```bash
java -version   # must report 11+
```

If `java -version` reports an older version but Java 11 is installed, set `JAVA_HOME`
to your JDK 11 install before building/running:

```powershell
# Windows (PowerShell)
$env:JAVA_HOME = "C:\Program Files\Java\jdk-11.0.11"
```

```bash
# Linux / macOS
export JAVA_HOME=$(/usr/libexec/java_home -v 11)
```

The Gradle build compiles with `--release 11`, so the JAR is always Java 11
bytecode (class file major version 55) even if your default `java` is newer. The
`runServer` task launches Paper with the resolved Java toolchain, which must be
Java 21+ to run a modern Paper server.

Java 8 will **not** work: a Java 11 class file cannot be loaded by a Java 8 JVM.

### Frontend not present in the JAR

The built frontend is written to `src/main/resources/webapp/`. If that directory is
empty, run:

```bash
cd web
npm install
npm run build
```

then rebuild the plugin with `./gradlew shadowJar`. The `webapp` folder is packaged into
the JAR automatically.

### `Plugin JAR not found at build/libs/SMPWrapped-0.0.1.jar`

`runServer` depends on `shadowJar`, but if the file was deleted manually, run
`./gradlew shadowJar` first.

## Runtime Issues

### Paper download fails / 404

`runServer` resolves the latest Paper build from the `fill.papermc.io` v3 API and falls
back to a pinned build. The old `api.papermc.io` v2 API has been sunset (returns
`{"error":"sunset"}`), which is why the newer endpoint is used. If the download fails,
download `paper-1.21.11-<build>.jar` manually from
<https://papermc.io/downloads/paper> and place it at `build/server/paper-1.21.11.jar`,
then run `./gradlew runServer` again.

### Web dashboard is not reachable

1. Confirm `web.enabled: true` in `plugins/SMPWrapped/config.yml`.
2. Confirm the server started without plugin errors (look for
   `SMPWrapped enabled successfully!` and `Web server started on ...`).
3. The default bind is `127.0.0.1:8080` — it is only reachable from the same machine.
   To expose it, see [SECURITY.md](SECURITY.md).
4. Check that no other process uses port 8080.

### Port already in use

Change `web.port` in `config.yml` and restart (or `/wrapped reload` for non-bind
settings; a port change requires a server restart).

### Admin API returns 401 Unauthorized

The admin token is generated on first start and printed to the console:

```
Admin API token: <token>
```

Set it explicitly in `config.yml` under `admin.token` if you lost it. Always send it as
`Authorization: Bearer <token>`.

### Statistics look wrong / duplicated

- Block breaking is counted from `BlockBreakEvent`, not inferred from inventories.
- Creative-mode block breaks are intentionally not counted as mining.
- Playtime is derived from login/logout sessions; a hard crash without plugin disable
  will close the session on the next startup boundary. Server-level session data is
  flushed on `/wrapped reload`, plugin disable, and the autosave interval.
- Statistics cannot be reconstructed retroactively — tracking only begins once the
  plugin is installed.

### Database errors / corruption

- SQLite runs in WAL mode with `synchronous=NORMAL`; the database lives at
  `plugins/SMPWrapped/smpwrapped.db` (configurable).
- Create a backup with `/wrapped admin backup` before risky operations.
- If the database is corrupt, stop the server, move `smpwrapped.db*` aside, and restart
  to recreate a fresh schema.

### Configuration validation failure on startup

The plugin refuses to start with invalid configuration and logs each error, e.g.:

```
Config validation error: web.port must be between 1 and 65535
```

Fix the reported values in `config.yml` and restart.

### `admin.token must not be empty` on startup

This was a bug in earlier builds where the shipped `config.yml` contained an empty
`admin.token` and validation rejected it. The plugin now auto-generates a token when the
value is empty and saves it to `config.yml`. If you see this error, you are running an
old JAR — rebuild with `./gradlew shadowJar`.

### Cosmetic startup messages

Some startup lines are informational and safe to ignore:

- `SLF4J(W): No SLF4J providers were found` / the Javalin
  `It looks like you don't have a logger` banner — SMPWrapped bundles a relocated SLF4J
  provider (slf4j-jdk14), so logs from Javalin/Jetty/Hikari are routed through Paper. The
  banner should not appear in current builds; if it does, another plugin may be shipping
  a conflicting SLF4J binding.
- `Nag author(s): ... about their usage of System.out/err.print` — emitted by Paper when a
  bundled library writes to the process streams. It does not affect functionality and
  should not appear in current builds.
- `Javalin: ... your Javalin version is N days old` — Javalin's own version notice.
- `**** SERVER IS RUNNING IN OFFLINE/INSECURE MODE!` — expected for the `runServer` task,
  which sets `online-mode=false` for local testing only.

