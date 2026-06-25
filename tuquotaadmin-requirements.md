# Integración TuQuotaAdmin con Doorbell Detector

## Visión General

El sistema **Doorbell Detector** permite capturar notificaciones del timbre en un celular y retransmitirlas en tiempo real a otros dispositivos que tengan **TuQuotaAdmin** instalado, a través de un servidor WebSocket.

Este documento describe los requisitos de implementación en TuQuotaAdmin para recibir y mostrar estas notificaciones.

---

## Arquitectura

```
[App de timbre] → [Doorbell Detector] → [Backend Node.js] → [TuQuotaAdmin]
                                              ˇ
                                       [WebSocket Server]
```

---

## 1. Conexión WebSocket

TuQuotaAdmin debe implementar un **cliente WebSocket** que se conecte al servidor backend.

### 1.1 Configuración de URL

Agregar una pantalla de **Configuración** (Settings) en TuQuotaAdmin donde el usuario pueda ingresar la URL del servidor WebSocket.

- **Campo:** "URL del servidor Doorbell"
- **Formato:** `ws://ip-host:3000/ws` (local) o `wss://doorbell-api.onrender.com/ws` (producción)
- Almacenar en `SharedPreferences` o `DataStore`
- **Valor por defecto:** `ws://192.168.1.100:3000/ws`

### 1.2 Implementación del Cliente WebSocket

Usar **OkHttp WebSocket** (recomendado, ya que OkHttp suele estar presente) o la librería estándar `java.net.WebSocket`.

```kotlin
// Ejemplo con OkHttp
val client = OkHttpClient()
val request = Request.Builder().url(wsUrl).build()
val ws = client.newWebSocket(request, object : WebSocketListener() {

    override fun onOpen(webSocket: WebSocket, response: Response) {
        // Conexión establecida
    }

    override fun onMessage(webSocket: WebSocket, text: String) {
        // Procesar mensaje entrante
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        // Programar reconexión
    }

    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
        // Programar reconexión
    }
})
```

### 1.3 Reconexión Automática

Implementar reconexión con **backoff exponencial**:

| Intento | Espera |
|---------|--------|
| 1 | 1 segundo |
| 2 | 2 segundos |
| 3 | 4 segundos |
| 4 | 8 segundos |
| 5+ | 30 segundos (máximo) |

- Usar `CoroutineScope` o `Handler` para programar reconexiones
- Detener reconexión si el usuario cierra la pantalla o desactiva la función

### 1.4 Ciclo de Vida

- Conectar al iniciar la app (o al activar la función en settings)
- Desconectar al cerrar la app
- Mantener conexión en segundo plano usando un **ForegroundService** (similar a cómo Doorbell Detector mantiene la escucha)

---

## 2. Formato del Mensaje

El servidor envía mensajes JSON con la siguiente estructura:

```json
{
    "type": "doorbell_ring",
    "app_name": "com.doorbell.app",
    "title": "Alguien está en la puerta",
    "body": "Persona detectada - 12:30 PM",
    "received_at": "2026-06-24T12:30:00.000Z"
}
```

| Campo | Tipo | Descripción |
|-------|------|-------------|
| `type` | String | Siempre `"doorbell_ring"`. Usar para filtrar otros tipos futuros. |
| `app_name` | String | Package name de la app que originó la notificación |
| `title` | String | Título de la notificación capturada |
| `body` | String | Cuerpo/texto de la notificación |
| `received_at` | String (ISO 8601) | Marca de tiempo de cuando se recibió |

### Mensaje de conexión inicial

Al conectar, el servidor envía un mensaje de confirmación:

```json
{
    "type": "connected",
    "message": "Conectado al servidor Doorbell Detector"
}
```

---

## 3. Comportamiento al Recibir un "doorbell_ring"

### 3.1 Notificación Android

Mostrar una **notificación push local** de alta prioridad:

| Elemento | Valor |
|----------|-------|
| Título | `"🔔 Timbre!"` |
| Texto | El `body` de la notificación, o "Alguien está tocando el timbre" si está vacío |
| Prioridad | `NotificationCompat.PRIORITY_HIGH` |
| Canal | `doorbell_alerts` (crear canal en `onCreate`) |
| Importancia del canal | `NotificationManager.IMPORTANCE_HIGH` |
| Sonido | Sonido por defecto de notificación (o personalizable) |
| Vibración | Patrón por defecto |
| Auto-cancel | `true` |

La notificación debe aparecer incluso si la app está en segundo plano o la pantalla apagada.

### 3.2 Full-Screen Intent (Opcional, Recomendado)

En Android 10+, usar `fullScreenIntent` para mostrar la notificación como **actividad emergente** cuando el dispositivo está bloqueado:

```kotlin
val fullScreenIntent = Intent(this, DoorbellAlertActivity::class.java).apply {
    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
}
val fullScreenPendingIntent = PendingIntent.getActivity(
    this, 0, fullScreenIntent,
    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
)

val notification = NotificationCompat.Builder(this, CHANNEL_ID)
    .setSmallIcon(R.drawable.ic_doorbell)
    .setContentTitle("🔔 Timbre!")
    .setContentText(body)
    .setPriority(NotificationCompat.PRIORITY_HIGH)
    .setCategory(NotificationCompat.CATEGORY_ALARM)
    .setFullScreenIntent(fullScreenPendingIntent, true)
    .setAutoCancel(true)
    .build()
```

### 3.3 Sonido Personalizable (Opcional)

Agregar opción en settings para seleccionar un sonido diferente para las alertas del timbre.

---

## 4. Permisos Requeridos

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.VIBRATE" />
```

En **Android 13+ (API 33)**, solicitar `POST_NOTIFICATIONS` en runtime.

---

## 5. UI - Pantalla de Configuración

Agregar una sección en los **Settings** de TuQuotaAdmin:

```
┌─────────────────────────────┐
│  Doorbell Detector          │
│                             │
│  URL del servidor:          │
│  ┌───────────────────────┐  │
│  │ ws://192.168.1.100:3000│  │
│  └───────────────────────┘  │
│                             │
│  [  Probar conexión  ]      │
│                             │
│  Estado: ● Conectado        │
│                             │
│  [✓] Notificar cuando       │
│      alguien toque el       │
│      timbre                 │
│                             │
│  Sonido: Timbre por defecto │
│         [  Seleccionar  ]   │
└─────────────────────────────┘
```

### Elementos:
1. **Campo de texto** para la URL del WebSocket
2. **Botón** "Probar conexión" que intenta conectar y muestra resultado
3. **Indicador de estado** (conectado/desconectado/reconectando)
4. **Switch** "Activar Doorbell Detector" para habilitar/deshabilitar
5. **Selector de sonido** (opcional)

---

## 6. Manejo de Estados

| Estado | Acción | UI |
|--------|--------|-----|
| Desconectado | Intentar reconexión cada 30s | Indicador rojo "Desconectado" |
| Conectando | Mostrar spinner | Indicador amarillo "Conectando..." |
| Conectado | Escuchar mensajes | Indicador verde "Conectado" |
| Error | Mostrar mensaje de error | Indicador rojo con texto del error |

---

## 7. Dependencias Sugeridas

```kotlin
// OkHttp (para WebSocket)
implementation("com.squareup.okhttp3:okhttp:4.12.0")

// Corrutinas
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

// JSON parsing
// Usar el JSONObject nativo de Android (org.json) o kotlinx.serialization
```

---

## 8. Resumen de Archivos a Crear/Modificar

| Archivo | Acción | Propósito |
|---------|--------|-----------|
| `WebSocketClient.kt` | **Crear** | Cliente WebSocket con reconexión automática |
| `DoorbellAlertService.kt` | **Crear** | Foreground service para mantener conexión |
| `DoorbellAlertActivity.kt` | **Crear** | Full-screen activity para alerta emergente |
| `SettingsFragment.kt` | **Modificar** | Agregar sección de configuración Doorbell |
| `AndroidManifest.xml` | **Modificar** | Agregar permisos y servicios |

---

## 9. Prueba de Integración

1. Iniciar el backend localmente: `cd backend && node src/index.js`
2. Abrir Doorbell Detector, seleccionar app y configurar URL `ws://localhost:3000`
3. En TuQuotaAdmin, configurar la misma URL del WebSocket
4. Generar una notificación en la app monitoreada
5. Verificar que TuQuotaAdmin recibe la alerta

---

## Notas Adicionales

- El WebSocket solo se usa para **broadcast en tiempo real**. No hay polling ni REST polling necesarios.
- Si se pierde la conexión, las notificaciones no se almacenan para replay (diseño intencional para mantenerlo simple).
- Para producción, se recomienda usar `wss://` (WebSocket seguro) cuando el backend esté en Render.
