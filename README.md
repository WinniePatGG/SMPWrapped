# SMPWrapped

**Minecraft SMP Wrapped Plugin + Web Dashboard**

SMPWrapped is a Minecraft plugin that tracks player statistics across an SMP season and generates beautiful, personalized Minecraft Wrapped pages that players can open in their web browser and share with friends. It runs on anything from 1.16.3 to 26.3 on a single jar.

## Features

- **Works everywhere**: one jar for Minecraft 1.16.3 → 26.3, on Paper and Spigot
- **Comprehensive Statistics Tracking**: Playtime, blocks mined/placed, distance traveled, mobs killed, deaths, damage, crafting, fishing, dimensions, unique days, and login sessions
- **Fullscreen Wrapped Story**: A Spotify-style slide-by-slide experience with auto-advance, progress bar, and a full dashboard at the end
- **62 Achievements**: Across 5 tiers and 12 categories, earned automatically (Night Owl, Early Bird, Diamond Hands, Dragon Slayer and more)
- **Percentile Rankings**: Every stat is ranked server-wide and shown as "Top X%"
- **6 Player Archetypes**: The Miner, The Explorer, The Builder, The Monster Hunter, The Survivor, and The Collector
- **Automatic Generation**: Weekly and monthly Wrapped runs on a schedule you choose, with catch-up for missed runs
- **Season Management**: Create, switch, end, publish and unpublish seasons with full data retention
- **Advanced Logging**: Structured, rotating, gzipped log files with stack traces, error deduplication and secret redaction
- **Embedded Web Server**: Javalin-based REST API with rate limiting, CORS, and admin authentication
- **Modern React Frontend**: Built with TypeScript, Tailwind CSS, and Framer Motion
- **SQLite Database**: With HikariCP connection pooling, async writes, and timestamped backups

## Quick Start

### Requirements

- Java 11 or newer
- Minecraft 1.16.3 up to 26.3
- Paper, Spigot or a fork of either
- Node.js 20+ (for frontend development only)

### Installation

1. Download the latest `SMPWrapped-0.0.1.jar` from the releases page
2. Place it in your server's `plugins/` folder
3. Start your Paper server
4. Configure `plugins/SMPWrapped/config.yml`
5. Restart the server

### Building from Source

```bash
# Build the plugin JAR (includes frontend)
./gradlew shadowJar

# Output: build/libs/SMPWrapped-0.0.1.jar
```

### Running a Local Test Server

A `runServer` Gradle task is provided. It downloads Paper, copies the built plugin, and
starts a test server automatically:

```bash
# Requires Java 11+ on JAVA_HOME
./gradlew runServer
```

On first run it will:
1. Download a Paper server JAR (1.16.5 or newer) to `build/server/`
2. Copy `SMPWrapped-0.0.1.jar` into `build/server/plugins/`
3. Create `eula.txt` and a basic `server.properties` (offline mode, port 25565)
4. Launch the server (2 GB heap, `--nogui`)

The server data lives in `build/server/`, which is git-ignored. To connect, launch
Minecraft 1.16.5 or newer and join `localhost:25565`. When the server is up you can visit the
dashboard at `http://localhost:8080`.

Configuration for the test server lives in `build/server/plugins/SMPWrapped/config.yml`.
Override the server heap with `-Psmppwrapped.memory`, e.g. `./gradlew runServer -Psmppwrapped.memory=4G`.

If the server or dashboard misbehaves, see [TROUBLESHOOTING.md](docs/TROUBLESHOOTING.md).

**IDE support**: shared run configurations are included in `.run/` for IntelliJ IDEA:
- `Run Paper Server` — runs `runServer`
- `Build Plugin JAR` — runs `clean shadowJar`
- `Run Tests` — runs `test`

### Frontend Development

```bash
cd web
npm install
npm run dev    # Dev server with hot reload
npm run build  # Production build (outputs to src/main/resources/webapp)
```

## Configuration

See [CONFIGURATION.md](docs/CONFIGURATION.md) for detailed configuration options.

## API Documentation

See [API.md](docs/API.md) for the complete REST API reference.

## Commands & Permissions

See [COMMANDS.md](docs/COMMANDS.md) for all available commands and permissions.

## Security

See [SECURITY.md](docs/SECURITY.md) for security best practices and reverse proxy setup.

## Project Structure

```
SMPWrapped/
├── src/
│   ├── main/
│   │   ├── java/de/winniepat/smpwrapped/
│   │   │   ├── SMPWrapped.java          # Main plugin class
│   │   │   ├── config/                  # Configuration management
│   │   │   ├── db/                      # Database layer (SQLite + HikariCP)
│   │   │   ├── model/                   # Data models
│   │   │   ├── tracking/                # Event listeners & stat tracking
│   │   │   ├── season/                  # Season lifecycle management
│   │   │   ├── wrapped/                 # Wrapped generation (archetypes, percentiles)
│   │   │   ├── web/                     # Embedded web server & REST API
│   │   │   └── command/                 # Commands & tab completion
│   │   ├── resources/
│   │   │   ├── plugin.yml               # Plugin descriptor
│   │   │   ├── config.yml               # Default configuration
│   │   │   └── webapp/                  # Built frontend assets
│   │   └── test/                        # Unit tests
│   └── web/                             # Frontend source (React + Vite)
├── build.gradle.kts                     # Gradle build configuration
└── docs/                                # Documentation
```

## Architecture

### Backend

- **Java 11+** with the Spigot API 1.16.3 (runs up to 26.3)
- **Javalin 6** embedded web server
- **SQLite** with **HikariCP** connection pooling
- **Jackson** for JSON serialization
- Async database operations via dedicated executor service

### Frontend

- **React 18** with TypeScript
- **Vite 5** for building
- **Tailwind CSS 3** for styling
- **Framer Motion** for animations
- **Lucide React** for icons

## Testing

```bash
# Run all unit tests (26 tests)
./gradlew test

# Run with verbose output
./gradlew test --info
```

Unit tests cover aggregation, session duration, archetype selection, percentile ranking,
and API input validation. They do **not** require a running server.

For an end-to-end check, run `./gradlew runServer` and confirm the console shows
`SMPWrapped enabled successfully!` followed by `Web server started on 127.0.0.1:8080`,
then visit `http://localhost:8080` or call `http://localhost:8080/api/v1/health`.

## License

MIT License
