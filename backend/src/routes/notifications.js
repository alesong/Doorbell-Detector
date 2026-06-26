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

router.post('/', async (req, res) => {
  try {
    const { app_name, package_name, title, body } = req.body;
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
