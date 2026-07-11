const { Router } = require('express');
const { upsertUserPushToken, getUserPushToken } = require('../db');
const { sendDoorbellPush } = require('../push');

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

router.get('/log', (req, res) => {
  res.json(serverLog);
});

module.exports = router;
