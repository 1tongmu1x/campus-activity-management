FROM node:24-alpine AS build
WORKDIR /build
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
ARG PUBLIC_HOST
RUN VITE_PUBLIC_ORIGIN="https://${PUBLIC_HOST}" npm run build

FROM caddy:2.11-alpine
COPY --from=build /build/dist /srv
COPY docker/Caddyfile /etc/caddy/Caddyfile
