export async function readCache<T>(
  db: D1Database,
  key: string,
  maxAgeSeconds: number
): Promise<T | null> {
  const row = await db
    .prepare("SELECT payload_json, updated_at FROM kv_cache WHERE cache_key = ?")
    .bind(key)
    .first<{ payload_json: string; updated_at: number }>();

  if (!row) return null;
  const nowSec = Math.floor(Date.now() / 1000);
  if (nowSec - row.updated_at > maxAgeSeconds) return null;

  try {
    return JSON.parse(row.payload_json) as T;
  } catch {
    return null;
  }
}

export async function writeCache(
  db: D1Database,
  key: string,
  payload: unknown
): Promise<void> {
  const nowSec = Math.floor(Date.now() / 1000);
  await db
    .prepare(
      `INSERT INTO kv_cache (cache_key, payload_json, updated_at)
       VALUES (?, ?, ?)
       ON CONFLICT(cache_key) DO UPDATE SET
         payload_json = excluded.payload_json,
         updated_at = excluded.updated_at`
    )
    .bind(key, JSON.stringify(payload), nowSec)
    .run();
}
