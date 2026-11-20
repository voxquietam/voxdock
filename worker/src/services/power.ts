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

function evaluateCurrentStatus(slots: PowerSlot[]): {
  status: "on" | "off" | "possible";
  nextChange: string | null;
} {
  // Ukraine local time (UTC+2 / UTC+3); compute current hour in Europe/Kyiv
  const kyivHour = Number(
    new Intl.DateTimeFormat("en-GB", {
      timeZone: "Europe/Kyiv",
      hour: "2-digit",
      hour12: false
    }).format(new Date())
  );

  const currentSlot = slots.find((s) => kyivHour >= s.startHour && kyivHour < s.endHour);
  const status = currentSlot?.state ?? "on";

  const nextSlot = slots.find((s) => s.startHour > kyivHour && s.state !== status);
  const nextChange = nextSlot
    ? `${String(nextSlot.startHour).padStart(2, "0")}:00 (${nextSlot.state.toUpperCase()})`
    : null;

  return { status, nextChange };
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
      summary: "Set your Odesa address & DTEK queue group in Settings",
      nextChange: null,
      slots: [],
      updatedAt: cfg.updated_at
    };
  }

  const key = `power_schedule_${cfg.queue_group || "default"}`;
  const stored = await readCache<StoredSchedule>(env.DB, key, 86400);

  if (!stored || stored.slots.length === 0) {
    const addr = [cfg.street, cfg.house].filter(Boolean).join(" ");
    return {
      city: cfg.city,
      street: cfg.street,
      house: cfg.house,
      queueGroup: cfg.queue_group,
      status: "on",
      summary: cfg.queue_group
        ? `Queue ${cfg.queue_group}${addr ? ` · ${addr}` : ""} (no outages scheduled)`
        : `${addr} (set DTEK queue group to track slots)`,
      nextChange: null,
      slots: [],
      updatedAt: cfg.updated_at
    };
  }

  const evalRes = evaluateCurrentStatus(stored.slots);
  return {
    city: cfg.city,
    street: cfg.street,
    house: cfg.house,
    queueGroup: cfg.queue_group,
    status: evalRes.status,
    summary: stored.summary,
    nextChange: evalRes.nextChange,
    slots: stored.slots,
    updatedAt: stored.updatedAt
  };
}
