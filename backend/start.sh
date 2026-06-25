#!/usr/bin/env bash
set -e

cd "$(dirname "$0")"

# Lanzar el servidor
npm start

# Para consultar notificaciones (ejecutar en otra terminal):
# curl -s http://localhost:3000/api/notifications | jq .
