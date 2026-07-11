const Database = require('better-sqlite3');
const path = require('path');
const { EventEmitter } = require('events');

const DATA_FILE = path.join(__dirname, '..', 'data.db');
const notificationEvents = new EventEmitter();
notificationEvents.setMaxListeners(100);

let db;

function initDb() {
  db = new Database(DATA_FILE);
  db.pragma('journal_mode = WAL');
  db.pragma('foreign_keys = ON');

  db.exec(`
    CREATE TABLE IF NOT EXISTS notifications (
      id TEXT PRIMARY KEY,
      app_name TEXT NOT NULL DEFAULT '',
      package_name TEXT NOT NULL DEFAULT '',
      title TEXT NOT NULL DEFAULT '',
      body TEXT NOT NULL DEFAULT '',
      received_at TEXT NOT NULL
    )
  `);

  db.exec(`CREATE INDEX IF NOT EXISTS idx_notifications_received_at ON notifications(received_at DESC)`);

  db.exec(`
    CREATE TABLE IF NOT EXISTS users (
      id TEXT PRIMARY KEY,
      push_token TEXT,
      created_at TEXT NOT NULL DEFAULT (datetime('now'))
    )
  `);

  db.exec(`
    CREATE TABLE IF NOT EXISTS dwellings (
      id TEXT PRIMARY KEY,
      name TEXT NOT NULL DEFAULT '',
      address TEXT NOT NULL DEFAULT '',
      created_at TEXT NOT NULL DEFAULT (datetime('now'))
    )
  `);

  db.exec(`
    CREATE TABLE IF NOT EXISTS dwelling_residents (
      dwelling_id TEXT NOT NULL,
      user_id TEXT NOT NULL,
      PRIMARY KEY (dwelling_id, user_id),
      FOREIGN KEY (dwelling_id) REFERENCES dwellings(id),
      FOREIGN KEY (user_id) REFERENCES users(id)
    )
  `);

  db.exec(`
    CREATE TABLE IF NOT EXISTS resident_services (
      id TEXT PRIMARY KEY,
      dwelling_id TEXT NOT NULL,
      service_id TEXT NOT NULL,
      provider TEXT NOT NULL DEFAULT '',
      created_at TEXT NOT NULL DEFAULT (datetime('now')),
      FOREIGN KEY (dwelling_id) REFERENCES dwellings(id)
    )
  `);

  db.exec(`CREATE INDEX IF NOT EXISTS idx_resident_services_service_id ON resident_services(service_id)`);
  db.exec(`CREATE INDEX IF NOT EXISTS idx_users_push_token ON users(push_token)`);

  console.log(`Database initialized (SQLite: ${DATA_FILE})`);
}

function insertNotification(notification) {
  const stmt = db.prepare(`
    INSERT INTO notifications (id, app_name, package_name, title, body, received_at)
    VALUES (?, ?, ?, ?, ?, ?)
  `);

  stmt.run(
    notification.id,
    notification.app_name || '',
    notification.package_name || '',
    notification.title || '',
    notification.body || '',
    notification.received_at || new Date().toISOString(),
  );

  const inserted = db.prepare('SELECT * FROM notifications WHERE id = ?').get(notification.id);

  notificationEvents.emit('notification', inserted);

  return inserted;
}

function getNotifications(limit = 50) {
  const stmt = db.prepare('SELECT * FROM notifications ORDER BY received_at DESC LIMIT ?');
  return stmt.all(limit);
}

function deleteAllNotifications() {
  db.exec('DELETE FROM notifications');
}

function closeDb() {
  if (db) db.close();
}

function upsertUserPushToken(userId, pushToken) {
  const existing = db.prepare('SELECT id FROM users WHERE id = ?').get(userId);
  if (existing) {
    db.prepare('UPDATE users SET push_token = ? WHERE id = ?').run(pushToken, userId);
  } else {
    db.prepare('INSERT INTO users (id, push_token) VALUES (?, ?)').run(userId, pushToken);
  }
}

function getUserPushToken(userId) {
  const row = db.prepare('SELECT push_token FROM users WHERE id = ?').get(userId);
  return row ? row.push_token : null;
}

function getPushTokensByServiceId(serviceId) {
  const stmt = db.prepare(`
    SELECT u.id AS user_id, u.push_token
    FROM users u
    JOIN dwelling_residents dr ON dr.user_id = u.id
    JOIN dwellings d ON d.id = dr.dwelling_id
    JOIN resident_services rs ON rs.dwelling_id = d.id
    WHERE rs.service_id = ?
      AND rs.provider = 'Doorbell'
      AND u.push_token IS NOT NULL
  `);
  return stmt.all(serviceId);
}

function clearPushToken(userId) {
  db.prepare('UPDATE users SET push_token = NULL WHERE id = ?').run(userId);
}

module.exports = { initDb, insertNotification, getNotifications, deleteAllNotifications, closeDb, notificationEvents, upsertUserPushToken, getUserPushToken, getPushTokensByServiceId, clearPushToken };
