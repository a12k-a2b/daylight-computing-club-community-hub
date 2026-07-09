# Serves the club (site/) as a static site — used by Railway, which hosts
# daylightcomputer.club. Railway auto-redeploys on every push to master.
FROM caddy:2-alpine
COPY Caddyfile /etc/caddy/Caddyfile
COPY site /srv
