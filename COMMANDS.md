# SMPWrapped Commands & Permissions

## Commands

### `/wrapped`
Open your Wrapped page in the browser via a clickable chat link.

**Permission**: `smpwrapped.use` (default: true)

### `/wrapped stats`
Display your current-season statistics in chat.

**Permission**: `smpwrapped.use`

### `/wrapped top`
Show a leaderboard preview in chat.

**Permission**: `smpwrapped.use`

### `/wrapped help`
Display available commands.

**Permission**: `smpwrapped.use`

### `/wrapped reload`
Reload configuration and flush stats to database.

**Permission**: `smpwrapped.admin` (default: op)

### `/wrapped admin create <name>`
Create a new season.

**Permission**: `smpwrapped.admin`

### `/wrapped admin end <season>`
End and archive a season.

**Permission**: `smpwrapped.admin`

### `/wrapped admin publish <season>`
Publish a season's Wrapped pages.

**Permission**: `smpwrapped.admin`

### `/wrapped admin stats <player>`
Inspect a player's tracked statistics.

**Permission**: `smpwrapped.admin`

### `/wrapped admin backup`
Create a database backup.

**Permission**: `smpwrapped.admin`

## Permissions

| Permission | Description | Default |
|------------|-------------|---------|
| `smpwrapped.use` | Use basic commands | true |
| `smpwrapped.admin` | Use admin commands | op |

## Tab Completion

All commands support tab completion for:
- Subcommands (`stats`, `top`, `help`, `reload`, `admin`)
- Admin subcommands (`create`, `end`, `publish`, `stats`, `backup`)
- Season IDs (for `end` and `publish`)
- Player names (for `stats`)
