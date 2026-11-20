# VoxDock

Personal ambient dock and notification hub for Android, backed by a Cloudflare Worker with D1 and a Streamable HTTP MCP server for AI agents.

## Architecture

```
[AI Agents (Claude Code / Antigravity)]
            │  MCP (Streamable HTTP JSON-RPC)
            ▼
  Cloudflare Worker + D1 (worker/)
   ├── /mcp           — push_note, push_reminder, set_agent_status, list_notes, update_power_schedule
   ├── /api/dashboard — air raid alerts, 10-day weather + sea temp, Unseen anomalies, power queue, MCP items
   └── Cron trigger   — refreshes Unseen anomaly engine every 2 hours and caches results in D1
            │  HTTPS
            ▼
  Android App (android/ — Kotlin + Jetpack Compose)
   ├── Responsive Bento grid with monospace typography (Landscape 2-col + Portrait 1-col)
   ├── Always-on Dock Mode (keep screen on, immersive bars, 30s auto-dim, Night OLED quiet hours)
   ├── Air raid alerts (green/yellow/red) + fullscreen SVG vector map of Ukraine on tap
   ├── 10-day Open-Meteo weather forecast + sea surface temperature on tap
   ├── Unseen ("Nezryme") local anomaly engine + fullscreen details overlay on tap
   └── NotificationListenerService capture (Telegram, KSU24, Zoom) with PendingIntent jump & mute filters
```

## Quick Start

### 1. Cloudflare Worker (`worker/`)

```bash
cd worker
npm install
npx wrangler d1 create voxdock
# Put database_id into wrangler.toml
npx wrangler d1 migrations apply voxdock --remote
npx wrangler secret put VOXDOCK_TOKEN
# Optional: enables 3-level (partial/yellow) alerts via alerts.in.ua;
# without it, the worker automatically falls back to live ubilling.net.ua alerts.
npx wrangler secret put ALERTS_IN_UA_TOKEN
npm run deploy
```

### 2. Connect MCP (`Claude Code` / `Antigravity`)

Point your MCP client to `https://<your-worker>.workers.dev/mcp` with header `Authorization: Bearer <VOXDOCK_TOKEN>`:

```json
{
  "voxdock": {
    "type": "http",
    "url": "https://<your-worker>.workers.dev/mcp",
    "headers": {
      "Authorization": "Bearer <VOXDOCK_TOKEN>"
    }
  }
}
```

Available MCP tools:
- `push_note(text, pinned?, source?)`
- `push_reminder(text, remind_at, source?)`
- `set_agent_status(level, message, source?)`
- `list_notes()`
- `update_power_schedule(queue_group, summary, slots)`

### 3. Android APK (`android/`)

Requires JDK 17/21 and Android SDK 35 (`android/local.properties` with `sdk.dir=...`).

```bash
cd android
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

After installing on your Android phone:
1. Open **VoxDock** → **⚙ Settings** and enter your Worker URL, `VOXDOCK_TOKEN`, city, and DTEK address/queue.
2. Tap **GRANT NOTIFICATION ACCESS** so VoxDock can capture Telegram / Zoom / KSU24 notifications and jump straight into chats on tap.
3. Place the phone horizontally on your charging stand to trigger **Dock Mode**.

## License

See [LICENSE](LICENSE).
