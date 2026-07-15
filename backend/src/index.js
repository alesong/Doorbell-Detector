require('dotenv').config({ path: require('path').join(__dirname, '..', '.env') });

const express = require('express');
const http = require('http');
const path = require('path');
const cors = require('cors');
const { initDb } = require('./db');
const { setupWebSocket } = require('./websocket');
const notificationsRouter = require('./routes/notifications');
const residentServicesRouter = require('./routes/resident-services');

const PORT = process.env.PORT || 3000;

async function main() {
  const app = express();
  app.use(cors());
  app.use(express.json());

  app.use(express.static(path.join(__dirname, '..', 'public')));

  app.get('/health', (req, res) => {
    res.json({ status: 'ok', timestamp: new Date().toISOString() });
  });

  app.use('/api/notifications', notificationsRouter);
  app.use('/api/resident-services', residentServicesRouter);

  const server = http.createServer(app);
  setupWebSocket(server);

  initDb();

  server.listen(PORT, '0.0.0.0', () => {
    console.log(`Server running on port ${PORT}`);
  });
}

main().catch((err) => {
  console.error('Failed to start server:', err);
  process.exit(1);
});
