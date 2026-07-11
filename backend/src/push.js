const { Expo } = require('expo-server-sdk');
const { getPushTokensByServiceId, clearPushToken } = require('./db');

const expo = new Expo();

function isValidExpoToken(token) {
  return Expo.isExpoPushToken(token);
}

async function sendDoorbellPush(serviceId) {
  const residents = getPushTokensByServiceId(serviceId);

  if (residents.length === 0) {
    console.log(`[Push] No push tokens found for service ${serviceId}`);
    return { sent: 0, errors: 0 };
  }

  const messages = residents
    .filter(r => isValidExpoToken(r.push_token))
    .map(r => ({
      to: r.push_token,
      sound: 'doorbell.wav',
      title: '¡Alguien toca el timbre!',
      body: 'Alguien está en la puerta.',
      priority: 'high',
      channelId: 'doorbell',
      data: { type: 'doorbell', url: '/my-services' },
    }));

  if (messages.length === 0) {
    console.log(`[Push] No valid Expo push tokens for service ${serviceId}`);
    return { sent: 0, errors: 0 };
  }

  const chunks = expo.chunkPushNotifications(messages);
  let sent = 0;
  let errors = 0;

  for (const chunk of chunks) {
    try {
      const receipts = await expo.sendPushNotificationsAsync(chunk);
      for (let i = 0; i < receipts.length; i++) {
        const { status, details, message } = receipts[i];
        if (status === 'ok') {
          sent++;
        } else if (status === 'error') {
          errors++;
          if (details?.error === 'DeviceNotRegistered') {
            const token = messages[sent + errors - 1]?.to;
            const resident = residents.find(r => r.push_token === token);
            if (resident) {
              console.log(`[Push] DeviceNotRegistered for user ${resident.user_id}, clearing token`);
              clearPushToken(resident.user_id);
            }
          } else if (details?.error === 'MessageTooBig') {
            console.warn('[Push] MessageTooBig:', message);
          } else {
            console.error('[Push] Expo error:', details?.error, message);
          }
        }
      }
    } catch (error) {
      errors += chunk.length;
      console.error('[Push] Error sending chunk:', error.message);
    }
  }

  console.log(`[Push] Service ${serviceId}: sent=${sent} errors=${errors}`);
  return { sent, errors };
}

module.exports = { sendDoorbellPush };
