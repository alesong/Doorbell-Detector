const { Router } = require('express');
const { v4: uuidv4 } = require('uuid');
const { insertNotification, getNotifications, deleteAllNotifications, notificationEvents } = require('../db');
const { broadcastNotification } = require('../websocket');

const router = Router();

router.post('/', async (req, res) => {
  try {
    const { app_name, package_name, title, body } = req.body;

    if (!title && !body) {
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

    const stored = insertNotification(notification);
    broadcastNotification(stored);

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

router.delete('/', async (req, res) => {
  try {
    deleteAllNotifications();
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

  res.write('data: {"type":"connected"}\n\n');

  const onNotification = (notification) => {
    res.write(`data: ${JSON.stringify({ type: 'notification', notification })}\n\n`);
  };

  notificationEvents.on('notification', onNotification);

  const heartbeat = setInterval(() => {
    res.write(':heartbeat\n\n');
  }, 30000);

  req.on('close', () => {
    notificationEvents.off('notification', onNotification);
    clearInterval(heartbeat);
  });
});

module.exports = router;
