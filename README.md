# VoxDock

Personal ambient dock & notification hub for Android, backed by a Cloudflare Worker with D1 and an MCP endpoint for AI agents.

## Architecture

```
[AI Agents (Claude / Antigravity)]
            │  MCP (Streamable HTTP)
            ▼
  Cloudflare Worker + D1 (worker/)
   ├── /mcp           — push_note, push_reminder, set_agent_status, list_notes
   ├── /api/dashboard — alerts (3-level), 10-day weather + sea temp, Unseen anomalies, power queue, MCP items
   └── Cron trigger   — refreshes Unseen anomaly engine & caches results in D1
            │  HTTPS
            ▼
  Android App (android/ — Kotlin + Jetpack Compose)
   ├── Responsive Bento grid with monospace typography (Landscape + Portrait)
   ├── Always-on Dock mode (keep screen on, auto-dim, night OLED quiet hours)
   ├── 3-level air raid alerts + fullscreen vector map of Ukraine on tap
   ├── 10-day weather forecast + sea temperature on tap
   ├── Unseen ("Nezryme") local anomaly card with configurable categories
   └── NotificationListenerService capture (Telegram, KSU24, Zoom) with direct PendingIntent jump & mute filters
```

## Quick Start

### 1. Cloudflare Worker (`worker/`)

```bash
cd worker
npm install
npx wrangler d1 create voxdock
# Paste the generated database_id into wrangler.toml
npx wrangler d1 migrations apply voxdock --remote
npx wrangler secret put VOXDOCK_TOKEN
# Optional: air raid alerts token from alerts.in.ua
npx wrangler secret put ALERTS_IN_UA_TOKEN
npm run deploy
```

### 2. Android APK (`android/`)

```bash
cd android
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

After installing on your Android phone:
1. Open **VoxDock** → **Settings** and enter your Worker URL and `VOXDOCK_TOKEN`.
2. Grant **Notification Access** so VoxDock can capture and jump into Telegram / Zoom / KSU24 notifications.
3. Place the phone horizontally on your charging stand to trigger **Dock Mode**.

## License

See [LICENSE](LICENSE).
