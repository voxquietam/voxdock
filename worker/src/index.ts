import { isAuthorized, jsonResponse } from "./auth";
import { handleMcpRequest } from "./mcp";
import { getActaTasks } from "./services/acta";
import { getAlerts } from "./services/alerts";
import { getCurrency } from "./services/currency";
import { getPowerStatus, savePowerConfig } from "./services/power";
import { getSensors } from "./services/sensors";
import { getUnseen, refreshUnseen } from "./services/unseen";
import { getLatestUpdate } from "./services/update";
import { getWeather } from "./services/weather";
import type { AgentStatus, Env, NoteItem, UnseenCategory } from "./types";

async function listActiveNotes(db: D1Database): Promise<NoteItem[]> {
  const { results } = await db
    .prepare(
      `SELECT id, kind, text, remind_at, pinned, source, created_at
       FROM notes
       WHERE dismissed = 0
       ORDER BY pinned DESC, id DESC
       LIMIT 20`
    )
    .all<{
      id: number;
      kind: "note" | "reminder";
      text: string;
      remind_at: string | null;
      pinned: number;
      source: string;
      created_at: string;
    }>();

  return (results ?? []).map((r) => ({
    id: r.id,
    kind: r.kind,
    text: r.text,
    remindAt: r.remind_at,
    pinned: r.pinned === 1,
    source: r.source,
    createdAt: r.created_at
  }));
}

async function getAgentStatus(db: D1Database): Promise<AgentStatus> {
  const row = await db
    .prepare("SELECT level, message, source, updated_at FROM agent_status WHERE id = 1")
    .first<{
      level: AgentStatus["level"];
      message: string;
      source: string;
      updated_at: string;
    }>();

  return (
    row
      ? {
          level: row.level,
          message: row.message,
          source: row.source,
          updatedAt: row.updated_at
        }
      : {
          level: "ok",
          message: "VoxDock hub ready",
          source: "system",
          updatedAt: new Date().toISOString()
        }
  );
}

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    if (request.method === "OPTIONS") {
      return jsonResponse({ ok: true }, 204);
    }

    const url = new URL(request.url);
    if (url.pathname === "/health") {
      return jsonResponse({ ok: true, service: "voxdock-worker" });
    }

    if (!isAuthorized(request, env)) {
      return jsonResponse({ error: "Unauthorized" }, 401);
    }

    if (url.pathname === "/mcp") {
      return handleMcpRequest(request, env);
    }

    if (url.pathname === "/api/dashboard" && request.method === "GET") {
      const lat = Number(url.searchParams.get("lat") ?? env.DEFAULT_LAT ?? "46.48");
      const lon = Number(url.searchParams.get("lon") ?? env.DEFAULT_LON ?? "30.72");
      const city = url.searchParams.get("city") ?? env.DEFAULT_CITY ?? "Odesa";
      const oblastUid = Number(
        url.searchParams.get("oblastUid") ?? env.DEFAULT_OBLAST_UID ?? "18"
      );
      const unseenParam = url.searchParams.get("unseen");
      const enabledUnseen = unseenParam
        ? new Set(
            unseenParam
              .split(",")
              .map((s) => s.trim())
              .filter(Boolean) as UnseenCategory[]
          )
        : undefined;

      const [alerts, weather, currency, sensors, acta, unseen, power, notes, agentStatus, update] =
        await Promise.all([
          getAlerts(env, oblastUid),
          getWeather(env, lat, lon, city),
          getCurrency(env),
          getSensors(env, lat, lon),
          getActaTasks(env),
          getUnseen(env, lat, lon, enabledUnseen),
          getPowerStatus(env),
          listActiveNotes(env.DB),
          getAgentStatus(env.DB),
          getLatestUpdate(env)
        ]);

      return jsonResponse({
        serverTime: new Date().toISOString(),
        alerts,
        weather,
        currency,
        sensors,
        acta,
        unseen,
        power,
        notes,
        agentStatus,
        update
      });
    }

    if (url.pathname.startsWith("/api/notes/") && request.method === "DELETE") {
      const id = Number(url.pathname.split("/").pop());
      if (!Number.isFinite(id)) return jsonResponse({ error: "Invalid note id" }, 400);
      await env.DB.prepare("UPDATE notes SET dismissed = 1 WHERE id = ?").bind(id).run();
      return jsonResponse({ ok: true, dismissedId: id });
    }

    if (url.pathname === "/api/power/config" && request.method === "POST") {
      const body = (await request.json().catch(() => ({}))) as {
        city?: string;
        street?: string;
        house?: string;
        queueGroup?: string;
      };
      const updated = await savePowerConfig(env.DB, body);
      return jsonResponse({ ok: true, config: updated });
    }

    return jsonResponse({ error: "Not found" }, 404);
  },

  async scheduled(_event: ScheduledEvent, env: Env): Promise<void> {
    const lat = Number(env.DEFAULT_LAT ?? "46.48");
    const lon = Number(env.DEFAULT_LON ?? "30.72");
    await refreshUnseen(env, lat, lon);
  }
};
