const { Router } = require('express');
const { v4: uuidv4 } = require('uuid');
const { insertNotification, getNotifications, deleteAllNotifications, notificationEvents } = require('../db');
const { broadcastNotification } = require('../websocket');

const router = Router();

const serverLog = [];
const MAX_LOG = 200;

function addLog(event, detail) {
  const entry = { ts: new Date().toISOString(), event, detail };
  serverLog.unshift(entry);
  if (serverLog.length > MAX_LOG) serverLog.length = MAX_LOG;
  console.log(`[${entry.ts}] ${event}: ${detail}`);
}

async function triggerTuQuotaPush(serviceId, apiKeyFromRequest) {
  const apiUrl = process.env.TUQUOTAADMIN_API_URL;
  const apiKey = apiKeyFromRequest || process.env.TUQUOTAADMIN_API_KEY;
  console.log(`[TQUOTA DEBUG] serviceId="${serviceId}" apiKey="${apiKey}" length=${apiKey?.length} source=${apiKeyFromRequest ? 'from_request' : 'from_env'}`);
  if (!apiUrl || !apiKey) {
    addLog('TQUOTA', `Skipped: TUQUOTAADMIN_API_URL or TUQUOTAADMIN_API_KEY not configured`);
    return { called: false, reason: 'not_configured' };
  }

  try {
    const urlStr = `${apiUrl.replace(/\/+$/, '')}/public/doorbell/ring`;
    const parsedUrl = new URL(urlStr);
    const mod = parsedUrl.protocol === 'https:' ? require('https') : require('http');

    const body = JSON.stringify({ serviceId, apiKey });

    const response = await new Promise((resolve, reject) => {
      const opts = {
        hostname: parsedUrl.hostname,
        port: parsedUrl.port || (parsedUrl.protocol === 'https:' ? 443 : 80),
        path: parsedUrl.pathname + parsedUrl.search,
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Content-Length': Buffer.byteLength(body),
        },
      };
      const req = mod.request(opts, (res) => {
        let data = '';
        res.on('data', (chunk) => { data += chunk; });
        res.on('end', () => resolve({ status: res.statusCode, body: data }));
      });
      req.on('error', reject);
      req.write(body);
      req.end();
    });

    addLog('TQUOTA', `POST /public/doorbell/ring service=${serviceId} status=${response.status}`);
    return { called: true, status: response.status };
  } catch (err) {
    addLog('TQUOTA', `Error calling TuQuotaAdmin: ${err.message}`);
    return { called: true, error: err.message };
  }
}

router.post('/', async (req, res) => {
  try {
    const { app_name, package_name, title, body, service_id, api_key } = req.body;
    const startMs = Date.now();

    if (!title && !body) {
      addLog('POST', 'Rejected: title and body empty');
      return res.status(400).json({ error: 'title or body is required' });
    }

    const notification = {
      id: uuidv4(),
      app_name: app_name || '',
      package_name: package_name || '',
      title: title || '',
      body: body || '',
      received_at: new Date().toISOString(),
    };

    addLog('POST', `Received from=${app_name} pkg=${package_name} id=${notification.id} title="${title}"`);

    const stored = insertNotification(notification);
    addLog('DB', `Stored id=${notification.id}`);

    broadcastNotification(stored);
    addLog('WS', `Broadcast id=${notification.id}`);

    // If service_id is provided, trigger push notification via TuQuotaAdmin
    if (service_id) {
      addLog('POST', `Triggering push for service_id=${service_id}${api_key ? ' (con api_key)' : ' (sin api_key)'}`);
      triggerTuQuotaPush(service_id, api_key).then(result => {
        addLog('POST', `Push trigger result: ${JSON.stringify(result)}`);
      });
    } else {
      addLog('POST', `No service_id provided, skipping push trigger`);
    }

    const duration = Date.now() - startMs;
    addLog('POST', `Complete id=${notification.id} ${duration}ms`);

    res.status(201).json(stored);
  } catch (err) {
    console.error('Error storing notification:', err);
    res.status(500).json({ error: 'Internal server error' });
  }
});

router.get('/', async (req, res) => {
  try {
    res.set('Cache-Control', 'no-cache, no-store, must-revalidate');
    const limit = parseInt(req.query.limit) || 50;
    const notifications = getNotifications(limit);
    res.json(notifications);
  } catch (err) {
    console.error('Error fetching notifications:', err);
    res.status(500).json({ error: 'Internal server error' });
  }
});

router.get('/log', (req, res) => {
  res.json(serverLog);
});

router.delete('/', async (req, res) => {
  try {
    deleteAllNotifications();
    addLog('DELETE', 'All notifications deleted');
    res.json({ message: 'All notifications deleted' });
  } catch (err) {
    console.error('Error deleting notifications:', err);
    res.status(500).json({ error: 'Internal server error' });
  }
});

router.get('/stream', (req, res) => {
  res.writeHead(200, {
    'Content-Type': 'text/event-stream',
    'Cache-Control': 'no-cache',
    'Connection': 'keep-alive',
    'X-Accel-Buffering': 'no',
  });

  addLog('SSE', 'Client connected');
  res.write('data: {"type":"connected"}\n\n');

  const onNotification = (notification) => {
    const msg = `data: ${JSON.stringify({ type: 'notification', notification })}\n\n`;
    res.write(msg);
  };

  notificationEvents.on('notification', onNotification);

  const heartbeat = setInterval(() => {
    res.write(':heartbeat\n\n');
  }, 30000);

  req.on('close', () => {
    addLog('SSE', 'Client disconnected');
    notificationEvents.off('notification', onNotification);
    clearInterval(heartbeat);
  });
});

module.exports = router;
