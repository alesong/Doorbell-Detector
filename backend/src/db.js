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

module.exports = { initDb, insertNotification, getNotifications, deleteAllNotifications, closeDb, notificationEvents };
