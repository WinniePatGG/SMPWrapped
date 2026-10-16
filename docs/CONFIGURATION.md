# SMPWrapped Configuration

## config.yml Reference

### Web Server

```yaml
web:
  enabled: true                    # Enable/disable the web server
  host: 127.0.0.1                  # Bind address (use 127.0.0.1 for localhost only)
  port: 8080                       # Port to listen on
  public-base-url: http://localhost:8080  # Public URL for share links
  cache-seconds: 300               # Cache duration for web responses
```

**Security Note**: The default bind address is `127.0.0.1` (localhost only). To expose the web server publicly, use a reverse proxy (see [SECURITY.md](SECURITY.md)).

### Database

```yaml
database:
  path: smpwrapped.db              # Path relative to plugin folder
```

### Season

```yaml
season:
  current: season-1                # ID of the current active season
```

### Tracking

```yaml
tracking:
  playtime: true                   # Track playtime via login/logout sessions
  blocks: true                     # Track blocks mined and placed
  distance: true                   # Track distance traveled
  mobs: true                       # Track mobs killed
  deaths: true                     # Track player deaths
  damage: true                     # Track damage dealt and received
  crafting: true                   # Track items crafted and collected
  fishing: true                    # Track fishing catches
  dimensions: true                 # Track time in different dimensions
  sessions: true                   # Track login sessions
  achievements: true               # Track achievements
```

### Autosave

```yaml
autosave:
  interval-seconds: 120            # How often to flush stats to database
```

### Rate Limiting

```yaml
rate-limit:
  requests-per-minute: 60          # Max requests per minute per IP
```

### Admin

```yaml
admin:
  token: ""                        # Admin API token (auto-generated on first start)
```

**Important**: The admin token is automatically generated on first start and logged to the console. Store it securely.

### Leaderboard

```yaml
leaderboard:
  public: true                     # Allow public access to leaderboards
  min-sample-size: 3               # Minimum players required for percentile calculations
```

### Privacy

```yaml
privacy:
  player-profiles-public: true     # Allow public access to player profiles
  allow-player-opt-out: true       # Allow players to opt out of tracking
```

### Avatar

```yaml
avatar:
  url-template: "https://mc-heads.net/avatar/{uuid}/64"
```

Set to empty string to disable avatars. The `{uuid}` placeholder is replaced with the player's UUID.

### Branding

```yaml
branding:
  server-name: "Our SMP Server"    # Server name displayed on the site
  server-icon-url: ""              # URL to server icon
```

### Share Card

```yaml
share-card:
  enabled: true                    # Enable shareable Wrapped cards
  footer: "Wrapped with SMPWrapped" # Footer text on share cards
```

### Publication

```yaml
publication:
  auto-publish-on-end: false       # Automatically publish seasons when ended
```

## Configuration Validation

The plugin validates configuration on startup and will fail with clear error messages if:
- Port is out of range (1-65535)
- Autosave interval is less than 10 seconds
- Cache duration is negative
- Rate limit is less than 1
- Required fields are missing

## Reloading Configuration

Use `/wrapped reload` to reload configuration without restarting the server. This also flushes all pending stats to the database.
