# SMPWrapped REST API

## Base URL

```
http://localhost:8080/api/v1
```

## Public Endpoints

### Health Check

```
GET /health
```

Response:
```json
{
  "status": "ok",
  "version": "0.0.1",
  "timestamp": 1699999999999
}
```

### Seasons

```
GET /seasons
GET /seasons/current
GET /seasons/{seasonId}/stats
GET /seasons/{seasonId}/leaderboards/{stat}
```

### Players

```
GET /players/{playerId}
GET /players/by-name/{playerName}
GET /players/{playerId}/wrapped/{seasonId}
GET /players/{playerId}/stats/{seasonId}
```

`/players/by-name/{playerName}` resolves a Minecraft username to a tracked player
(case-insensitive) and returns the same payload as `/players/{playerId}`. It is
used by the Wrapped frontend to look a player up from the URL.

## Admin Endpoints

All admin endpoints require a Bearer token in the Authorization header:

```
Authorization: Bearer <admin-token>
```

### Season Management

```
POST /admin/seasons
POST /admin/seasons/{seasonId}/end
POST /admin/seasons/{seasonId}/publish
POST /admin/seasons/{seasonId}/unpublish
```

### Player Management

```
POST /admin/rebuild/{playerId}/{seasonId}
```

### Backup

```
POST /admin/backup
```

## Response Format

All responses use a consistent JSON structure:

```json
{
  "data": {},
  "error": null
}
```

Error responses:

```json
{
  "error": "Description of the error"
}
```

## Rate Limiting

- 60 requests per minute per IP (configurable)
- Returns 429 Too Many Requests when exceeded

## CORS

CORS is enabled for all origins by default. In production, restrict this via a reverse proxy.
