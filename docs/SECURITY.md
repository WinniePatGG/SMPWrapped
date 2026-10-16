# SMPWrapped Security Guide

## Default Security Posture

SMPWrapped is configured with secure defaults:

- Web server binds to `127.0.0.1` (localhost only)
- Admin API requires token authentication
- Rate limiting is enabled (60 requests/minute)
- CORS is enabled but should be restricted in production
- No default admin password (token auto-generated)
- All database queries use parameterized statements

## Exposing the Web Server Publicly

### Recommended: Reverse Proxy

Use nginx, Caddy, or Apache as a reverse proxy:

#### nginx Example

```nginx
server {
    listen 443 ssl http2;
    server_name wrapped.example.com;

    ssl_certificate /path/to/cert.pem;
    ssl_certificate_key /path/to/key.pem;

    location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

#### Caddy Example

```
wrapped.example.com {
    reverse_proxy 127.0.0.1:8080
}
```

### Security Headers

Add security headers in your reverse proxy:

```nginx
add_header X-Frame-Options "SAMEORIGIN" always;
add_header X-Content-Type-Options "nosniff" always;
add_header X-XSS-Protection "1; mode=block" always;
add_header Referrer-Policy "strict-origin-when-cross-origin" always;
```

## Admin Token Security

- The admin token is auto-generated on first start
- Store it securely (password manager, environment variable)
- Rotate the token by changing `admin.token` in config.yml and reloading
- Never commit the token to version control

## Player Privacy

- Set `privacy.player-profiles-public: false` to hide player profiles
- Set `privacy.allow-player-opt-out: true` to let players opt out
- Only publish statistics the server owner has chosen to make public

## Database Security

- The SQLite database is stored in the plugin folder
- Backups are created in `plugins/SMPWrapped/backups/`
- Use `/wrapped admin backup` to create manual backups
- Consider automated backup solutions for production

## Rate Limiting

Default: 60 requests per minute per IP. Adjust in config.yml:

```yaml
rate-limit:
  requests-per-minute: 100
```

## Input Validation

- All API endpoints validate input
- UUIDs are validated before database queries
- Season IDs are validated before use
- Player names are never used as database identifiers

## Known Limitations

- CORS is enabled for all origins by default (restrict via reverse proxy in production)
- SQLite is not suitable for very high-traffic servers (consider MySQL/PostgreSQL for large deployments)
- The embedded server is single-threaded for database operations (bounded by HikariCP pool size)
