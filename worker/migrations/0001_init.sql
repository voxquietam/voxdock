-- VoxDock D1 initial schema

CREATE TABLE IF NOT EXISTS notes (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    kind TEXT NOT NULL CHECK (kind IN ('note', 'reminder')),
    text TEXT NOT NULL,
    remind_at TEXT,
    pinned INTEGER NOT NULL DEFAULT 0,
    dismissed INTEGER NOT NULL DEFAULT 0,
    source TEXT NOT NULL DEFAULT 'mcp',
    created_at TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE INDEX IF NOT EXISTS idx_notes_active ON notes (dismissed, pinned, created_at);

CREATE TABLE IF NOT EXISTS agent_status (
    id INTEGER PRIMARY KEY CHECK (id = 1),
    level TEXT NOT NULL DEFAULT 'ok' CHECK (level IN ('ok', 'busy', 'warn', 'error')),
    message TEXT NOT NULL DEFAULT 'Idle',
    source TEXT NOT NULL DEFAULT 'mcp',
    updated_at TEXT NOT NULL DEFAULT (datetime('now'))
);

INSERT OR IGNORE INTO agent_status (id, level, message, source)
VALUES (1, 'ok', 'VoxDock hub ready', 'system');

CREATE TABLE IF NOT EXISTS kv_cache (
    cache_key TEXT PRIMARY KEY,
    payload_json TEXT NOT NULL,
    updated_at INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS power_config (
    id INTEGER PRIMARY KEY CHECK (id = 1),
    city TEXT NOT NULL DEFAULT 'Odesa',
    street TEXT NOT NULL DEFAULT '',
    house TEXT NOT NULL DEFAULT '',
    queue_group TEXT NOT NULL DEFAULT '',
    updated_at TEXT NOT NULL DEFAULT (datetime('now'))
);

INSERT OR IGNORE INTO power_config (id, city, street, house, queue_group)
VALUES (1, 'Odesa', '', '', '');
