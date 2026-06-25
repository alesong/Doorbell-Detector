const { WebSocketServer } = require('ws');

let wss = null;

function setupWebSocket(server) {
  wss = new WebSocketServer({ server });

  wss.on('connection', (ws) => {
    console.log('WebSocket client connected');

    ws.send(JSON.stringify({ type: 'connected', message: 'Conectado al servidor Doorbell Detector' }));

    ws.on('close', () => {
      console.log('WebSocket client disconnected');
    });

    ws.on('error', (err) => {
      console.error('WebSocket error:', err.message);
    });
  });

  console.log('WebSocket server ready');
}

function broadcastNotification(notification) {
  if (!wss) return;
  const message = JSON.stringify({
    type: 'doorbell_ring',
    app_name: notification.app_name,
    title: notification.title,
    body: notification.body,
    received_at: notification.received_at,
  });

  let count = 0;
  wss.clients.forEach((client) => {
    if (client.readyState === 1) {
      client.send(message);
      count++;
    }
  });

  console.log(`Broadcasted to ${count} client(s)`);
}

module.exports = { setupWebSocket, broadcastNotification };
