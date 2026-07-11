const { Router } = require('express');
const { upsertUserPushToken, getUserPushToken, getDbDiagnostics, seedTestData } = require('../db');
const { sendDoorbellPush, sendTestPush } = require('../push');

const router = Router();

const serverLog = [];
const MAX_LOG = 200;

function addLog(event, detail) {
  const entry = { ts: new Date().toISOString(), event, detail };
  serverLog.unshift(entry);
  if (serverLog.length > MAX_LOG) serverLog.length = MAX_LOG;
  console.log(`[${entry.ts}] ${event}: ${detail}`);
}

router.patch('/users/push-token', async (req, res) => {
  try {
    const { user_id, push_token } = req.body;

    if (!user_id) {
      addLog('PATCH /users/push-token', 'Rejected: user_id is required');
      return res.status(400).json({ success: false, error: 'user_id is required' });
    }

    if (!push_token || typeof push_token !== 'string') {
      addLog('PATCH /users/push-token', 'Rejected: push_token is required');
      return res.status(400).json({ success: false, error: 'push_token is required' });
    }

    upsertUserPushToken(user_id, push_token);
    addLog('PATCH /users/push-token', `user=${user_id} token=${push_token.substring(0, 20)}...`);

    res.json({ success: true, message: 'Push token actualizado' });
  } catch (err) {
    console.error('Error updating push token:', err);
    res.status(500).json({ success: false, error: 'Internal server error' });
  }
});

router.post('/doorbell/ring', async (req, res) => {
  try {
    const { service_id } = req.body;

    if (!service_id) {
      addLog('POST /doorbell/ring', 'Rejected: service_id is required');
      return res.status(400).json({ success: false, error: 'service_id is required' });
    }

    addLog('POST /doorbell/ring', `service=${service_id} — sending push notifications`);

    const result = await sendDoorbellPush(service_id);

    addLog('POST /doorbell/ring', `service=${service_id} sent=${result.sent} errors=${result.errors}`);

    res.json({ success: true, sent: result.sent, errors: result.errors });
  } catch (err) {
    console.error('Error processing doorbell ring:', err);
    res.status(500).json({ success: false, error: 'Internal server error' });
  }
});

router.post('/push/test', async (req, res) => {
  try {
    const { push_token, title, body } = req.body;

    if (!push_token) {
      addLog('POST /push/test', 'Rejected: push_token is required');
      return res.status(400).json({ success: false, error: 'push_token is required' });
    }

    addLog('POST /push/test', `token=${push_token.substring(0, 20)}... title="${title || ''}" body="${body || ''}"`);

    const result = await sendTestPush(push_token, title, body);

    addLog('POST /push/test', `sent=${result.sent} errors=${result.errors}${result.error ? ` error=${result.error}` : ''}`);

    res.json({ success: result.sent > 0, ...result });
  } catch (err) {
    console.error('Error sending test push:', err);
    res.status(500).json({ success: false, error: 'Internal server error' });
  }
});

router.get('/debug-db', (req, res) => {
  res.json(getDbDiagnostics());
});

router.post('/seed-data', (req, res) => {
  try {
    const { user_id, push_token, service_id } = req.body;

    if (!user_id || !push_token || !service_id) {
      addLog('POST /seed-data', 'Rejected: user_id, push_token, and service_id are required');
      return res.status(400).json({ success: false, error: 'user_id, push_token, and service_id are required' });
    }

    seedTestData(user_id, push_token, service_id);
    addLog('POST /seed-data', `user=${user_id} service=${service_id}`);

    res.json({ success: true, message: 'Test data seeded', diagnostics: getDbDiagnostics() });
  } catch (err) {
    console.error('Error seeding test data:', err);
    res.status(500).json({ success: false, error: 'Internal server error' });
  }
});

router.get('/log', (req, res) => {
  res.json(serverLog);
});

module.exports = router;
