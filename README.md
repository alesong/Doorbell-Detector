# Doorbell Detector

Sistema para capturar notificaciones de timbres inteligentes en Android y reenviarlas en tiempo real a un servidor propio.

## Arquitectura

```
App Android (Listener)  ──HTTP──>  Backend (Node.js)  ──WebSocket/SSE──>  Clientes Web
                                     │
                                     ▼
                                  SQLite
```

### Componentes

- **`android/`** — App Android que escucha las notificaciones del sistema y reenvía las de apps seleccionadas (Tuya Smart, Smart Life, Ring, etc.) a tu servidor via HTTP.
- **`android-notifsender/`** — App Android utilitaria que permite enviar notificaciones de prueba desde el mismo dispositivo.
- **`backend/`** — Servidor Node.js con Express que recibe las notificaciones, las almacena en SQLite y las difunde en tiempo real via WebSocket y SSE.
- **Web UI** — Interfaz web incluida en el backend para ver notificaciones en vivo y un simulador para pruebas.

## Instalación

### Backend

```bash
cd backend
npm install
cp .env.example .env   # Edita el puerto si es necesario
npm start
```

El servidor arranca en `http://0.0.0.0:3000`.

### App Android

1. Abre `android/` en Android Studio.
2. Conecta tu dispositivo o inicia un emulador.
3. Compila e instala la app.

### App NotifSender (opcional)

```bash
cd android-notifsender
./gradlew installDebug
```

## Manual de uso

### 1. Configurar la app Android

1. Abre Doorbell Detector en tu dispositivo.
2. Acepta la pantalla de privacidad.
3. Concede acceso a notificaciones:
   - Ve a Ajustes → Acceso a notificaciones.
   - Activa Doorbell Detector.
   - En Xiaomi/HyperOS activa "Permitir configuración restringida" primero.
4. Presiona **"Cargar apps"** y selecciona las apps de timbre a monitorear (Tuya, Smart Life, Ring, etc.).
5. Configura la **URL del servidor** (ej: `http://192.168.1.100:3000`).
6. Presiona **"Probar conexión"** para verificar.
7. Presiona **"Iniciar / Reiniciar servicio de escucha"**.

### 2. Ver notificaciones en vivo

Abre en un navegador: `http://<IP>:3000`

Las notificaciones aparecen en tiempo real via WebSocket.

### 3. Simulador de pruebas

- Web: `http://<IP>:3000/simulator.html`
- Android: usa la pestaña "Simulador" dentro de la app, o la app NotifSender.

## API

| Método | Ruta | Descripción |
|--------|------|-------------|
| `GET` | `/health` | Health check |
| `POST` | `/api/notifications` | Crear notificación (`app_name`, `package_name`, `title`, `body`) |
| `GET` | `/api/notifications` | Listar notificaciones (`?limit=N`) |
| `GET` | `/api/notifications/log` | Log del servidor |
| `DELETE` | `/api/notifications` | Eliminar todas |
| `GET` | `/api/notifications/stream` | SSE en tiempo real |
| WebSocket | `ws://<host>:3000` | Eventos `doorbell_ring` |

## Tecnologías

- **Android**: Kotlin, Jetpack Compose, OkHttp, DataStore
- **Backend**: Node.js, Express, better-sqlite3, ws, uuid
- **Tiempo real**: WebSocket + SSE
