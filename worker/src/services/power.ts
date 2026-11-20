import { readCache, writeCache } from "../cache";
import type { Env, PowerPayload, PowerSlot } from "../types";

interface PowerConfigRow {
  city: string;
  street: string;
  house: string;
  queue_group: string;
  updated_at: string;
}

interface StoredSchedule {
  slots: PowerSlot[];
  summary: string;
  updatedAt: string;
}

interface OutageDataUaResponse {
  lastUpdated?: string;
  fact?: {
    today?: number;
    data?: Record<string, Record<string, Record<string, string>>>;
  };
}

export async function getPowerConfig(db: D1Database): Promise<PowerConfigRow> {
  const row = await db
    .prepare("SELECT city, street, house, queue_group, updated_at FROM power_config WHERE id = 1")
    .first<PowerConfigRow>();

  return (
    row ?? {
      city: "Odesa",
      street: "",
      house: "",
      queue_group: "",
      updated_at: new Date().toISOString()
    }
  );
}

export async function savePowerConfig(
  db: D1Database,
  input: { city?: string; street?: string; house?: string; queueGroup?: string }
): Promise<PowerConfigRow> {
  const cur = await getPowerConfig(db);
  const city = (input.city ?? cur.city).trim() || "Odesa";
  const street = (input.street ?? cur.street).trim();
  const house = (input.house ?? cur.house).trim();
  const queueGroup = (input.queueGroup ?? cur.queue_group).trim();

  await db
    .prepare(
      `UPDATE power_config
       SET city = ?, street = ?, house = ?, queue_group = ?, updated_at = datetime('now')
       WHERE id = 1`
    )
    .bind(city, street, house, queueGroup)
    .run();

  return getPowerConfig(db);
}

export async function savePowerSchedule(
  db: D1Database,
  queueGroup: string,
  slots: PowerSlot[],
  summary: string
): Promise<void> {
  const key = `power_schedule_${queueGroup || "default"}`;
  const payload: StoredSchedule = {
    slots,
    summary,
    updatedAt: new Date().toISOString()
  };
  await writeCache(db, key, payload);
}

function normalizeGpvKey(raw: string): string {
  const clean = raw.trim().toUpperCase().replace(/^GPV/, "");
  if (!clean) return "";
  if (clean.includes(".")) return `GPV${clean}`;
  return `GPV${clean}.1`;
}

function getKyivTime(): { hour: number; minute: number } {
  const parts = new Intl.DateTimeFormat("en-GB", {
    timeZone: "Europe/Kyiv",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false
  }).formatToParts(new Date());
  const hour = Number(parts.find((p) => p.type === "hour")?.value ?? 0);
  const minute = Number(parts.find((p) => p.type === "minute")?.value ?? 0);
  return { hour, minute };
}

async function fetchOdesaDtekFeed(db: D1Database): Promise<OutageDataUaResponse | null> {
  const cached = await readCache<OutageDataUaResponse>(db, "dtek_odesa_feed", 900);
  if (cached) return cached;

  try {
    const res = await fetch(
      "https://raw.githubusercontent.com/Baskerville42/outage-data-ua/main/data/odesa.json",
      { headers: { "User-Agent": "VoxDock/0.1" } }
    );
    if (!res.ok) return null;
    const data = (await res.json()) as OutageDataUaResponse;
    await writeCache(db, "dtek_odesa_feed", data);
    return data;
  } catch {
    return null;
  }
}

function formatHalfHour(halfIdx: number): string {
  const h = Math.floor(halfIdx / 2) % 24;
  const m = halfIdx % 2 === 1 ? "30" : "00";
  return `${String(h).padStart(2, "0")}:${m}`;
}

function evaluateDtekGroup(
  hoursMap: Record<string, string>
): { status: "on" | "off"; nextChange: string | null; slots: PowerSlot[] } {
  // Build 48 half-hour booleans (true = power ON, false = power OFF)
  const halfOn: boolean[] = [];
  const slots: PowerSlot[] = [];

  for (let h = 1; h <= 24; h++) {
    const val = hoursMap[String(h)] ?? "yes";
    const firstHalfOn = val === "yes" || val === "second";
    const secondHalfOn = val === "yes" || val === "first";
    halfOn.push(firstHalfOn, secondHalfOn);

    const state: PowerSlot["state"] =
      val === "no" ? "off" : val === "first" || val === "second" ? "possible" : "on";
    slots.push({ startHour: h - 1, endHour: h, state });
  }

  const { hour, minute } = getKyivTime();
  const curIdx = Math.min(47, hour * 2 + (minute >= 30 ? 1 : 0));
  const isNowOn = halfOn[curIdx] ?? true;

  let nextIdx = -1;
  for (let i = curIdx + 1; i < 48; i++) {
    if (halfOn[i] !== isNowOn) {
      nextIdx = i;
      break;
    }
  }

  const nextChange =
    nextIdx >= 0
      ? `${isNowOn ? "OFF" : "ON"} ${formatHalfHour(nextIdx)}`
      : null;

  return { status: isNowOn ? "on" : "off", nextChange, slots };
}

export async function getPowerStatus(env: Env): Promise<PowerPayload> {
  const cfg = await getPowerConfig(env.DB);
  if (!cfg.queue_group && !cfg.street) {
    return {
      city: cfg.city,
      street: cfg.street,
      house: cfg.house,
      queueGroup: cfg.queue_group,
      status: "unconfigured",
      summary: "Set your Odesa address & DTEK queue (e.g. 2.1) in Settings",
      nextChange: null,
      slots: [],
      updatedAt: cfg.updated_at
    };
  }

  const addr = [cfg.street, cfg.house].filter(Boolean).join(" ");
  const gpvKey = normalizeGpvKey(cfg.queue_group);

  // 1. Try live DTEK Odesa community mirror (Baskerville42/outage-data-ua)
  if (gpvKey) {
    const feed = await fetchOdesaDtekFeed(env.DB);
    const todayKey = feed?.fact?.today ? String(feed.fact.today) : Object.keys(feed?.fact?.data ?? {})[0];
    const dayMap = todayKey ? feed?.fact?.data?.[todayKey] : undefined;
    const groupHours = dayMap?.[gpvKey];

    if (groupHours) {
      const ev = evaluateDtekGroup(groupHours);
      const label = ev.status === "on" ? "POWER ON" : "OUTAGE";
      const qLabel = gpvKey.replace("GPV", "");
      return {
        city: cfg.city,
        street: cfg.street,
        house: cfg.house,
        queueGroup: qLabel,
        status: ev.status,
        summary: `Q${qLabel} · ${label}${addr ? ` (${addr})` : ""}`,
        nextChange: ev.nextChange,
        slots: ev.slots,
        updatedAt: feed?.lastUpdated ?? new Date().toISOString()
      };
    }
  }

  // 2. Fallback to manual MCP-pushed schedule
  const key = `power_schedule_${cfg.queue_group || "default"}`;
  const stored = await readCache<StoredSchedule>(env.DB, key, 86400);
  if (stored && stored.slots.length > 0) {
    return {
      city: cfg.city,
      street: cfg.street,
      house: cfg.house,
      queueGroup: cfg.queue_group,
      status: "on",
      summary: stored.summary,
      nextChange: null,
      slots: stored.slots,
      updatedAt: stored.updatedAt
    };
  }

  return {
    city: cfg.city,
    street: cfg.street,
    house: cfg.house,
    queueGroup: cfg.queue_group,
    status: "on",
    summary: cfg.queue_group
      ? `Q${cfg.queue_group}${addr ? ` · ${addr}` : ""} (no outages scheduled)`
      : `${addr} (set DTEK queue like 2.1 in Settings)`,
    nextChange: null,
    slots: [],
    updatedAt: cfg.updated_at
  };
}
