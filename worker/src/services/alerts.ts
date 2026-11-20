import { readCache, writeCache } from "../cache";
import type { AlertLevel, AlertsPayload, Env, OblastAlert } from "../types";

// Official alerts.in.ua oblast order + Ukrainian state names used by ubilling.net.ua fallback.
const OBLAST_CATALOG: ReadonlyArray<{
  uid: number;
  code: string;
  name: string;
  ubillingKey: string;
}> = [
  { uid: 3, code: "KM", name: "Khmelnytskyi", ubillingKey: "Хмельницька область" },
  { uid: 4, code: "VI", name: "Vinnytsia", ubillingKey: "Вінницька область" },
  { uid: 5, code: "RV", name: "Rivne", ubillingKey: "Рівненська область" },
  { uid: 8, code: "VO", name: "Volyn", ubillingKey: "Волинська область" },
  { uid: 9, code: "DP", name: "Dnipropetrovsk", ubillingKey: "Дніпропетровська область" },
  { uid: 10, code: "ZT", name: "Zhytomyr", ubillingKey: "Житомирська область" },
  { uid: 11, code: "ZK", name: "Zakarpattia", ubillingKey: "Закарпатська область" },
  { uid: 12, code: "ZP", name: "Zaporizhzhia", ubillingKey: "Запорізька область" },
  { uid: 13, code: "IF", name: "Ivano-Frankivsk", ubillingKey: "Івано-Франківська область" },
  { uid: 14, code: "KV", name: "Kyiv Oblast", ubillingKey: "Київська область" },
  { uid: 15, code: "KR", name: "Kirovohrad", ubillingKey: "Кіровоградська область" },
  { uid: 16, code: "LG", name: "Luhansk", ubillingKey: "Луганська область" },
  { uid: 17, code: "MK", name: "Mykolaiv", ubillingKey: "Миколаївська область" },
  { uid: 18, code: "OD", name: "Odesa", ubillingKey: "Одеська область" },
  { uid: 19, code: "PL", name: "Poltava", ubillingKey: "Полтавська область" },
  { uid: 20, code: "SM", name: "Sumy", ubillingKey: "Сумська область" },
  { uid: 21, code: "TE", name: "Ternopil", ubillingKey: "Тернопільська область" },
  { uid: 22, code: "KH", name: "Kharkiv", ubillingKey: "Харківська область" },
  { uid: 23, code: "KS", name: "Kherson", ubillingKey: "Херсонська область" },
  { uid: 24, code: "CK", name: "Cherkasy", ubillingKey: "Черкаська область" },
  { uid: 25, code: "CH", name: "Chernihiv", ubillingKey: "Чернігівська область" },
  { uid: 26, code: "CV", name: "Chernivtsi", ubillingKey: "Чернівецька область" },
  { uid: 27, code: "LV", name: "Lviv", ubillingKey: "Львівська область" },
  { uid: 28, code: "DN", name: "Donetsk", ubillingKey: "Донецька область" },
  { uid: 29, code: "CR", name: "Crimea", ubillingKey: "Севастополь" },
  { uid: 30, code: "SV", name: "Sevastopol", ubillingKey: "Севастополь" },
  { uid: 31, code: "KC", name: "Kyiv City", ubillingKey: "м. Київ" }
];

function decodeChar(ch: string): AlertLevel {
  if (ch === "A") return "active";
  if (ch === "P") return "partial";
  return "none";
}

interface CachedAlerts {
  source: AlertsPayload["source"];
  oblasts: OblastAlert[];
}

async function fetchFromAlertsInUa(token: string): Promise<OblastAlert[] | null> {
  try {
    const res = await fetch(
      "https://api.alerts.in.ua/v1/iot/active_air_raid_alerts_by_oblast.json",
      {
        headers: {
          Authorization: `Bearer ${token}`,
          "User-Agent": "VoxDock/0.1"
        }
      }
    );
    if (!res.ok) return null;
    const rawText = (await res.text()).replace(/^"|"$/g, "").trim();
    return OBLAST_CATALOG.map((o, idx) => ({
      uid: o.uid,
      code: o.code,
      name: o.name,
      level: decodeChar(rawText[idx] ?? "N")
    }));
  } catch {
    return null;
  }
}

async function fetchFromUbilling(): Promise<OblastAlert[] | null> {
  try {
    const res = await fetch("https://ubilling.net.ua/aerialalerts/", {
      headers: { "User-Agent": "VoxDock/0.1" }
    });
    if (!res.ok) return null;
    const data = (await res.json()) as {
      states?: Record<string, { alertnow?: boolean }>;
    };
    const states = data.states;
    if (!states) return null;

    return OBLAST_CATALOG.map((o) => ({
      uid: o.uid,
      code: o.code,
      name: o.name,
      level: states[o.ubillingKey]?.alertnow ? "active" : "none"
    }));
  } catch {
    return null;
  }
}

export async function getAlerts(env: Env, selectedUid: number): Promise<AlertsPayload> {
  const cached = await readCache<CachedAlerts>(env.DB, "alerts_oblasts_v2", 20);
  let resolved = cached;

  if (!resolved) {
    const token = env.ALERTS_IN_UA_TOKEN?.trim();
    if (token) {
      const oblasts = await fetchFromAlertsInUa(token);
      if (oblasts) {
        resolved = { source: "alerts.in.ua", oblasts };
      }
    }

    if (!resolved) {
      const oblasts = await fetchFromUbilling();
      if (oblasts) {
        resolved = { source: "ubilling", oblasts };
      }
    }

    if (resolved) {
      await writeCache(env.DB, "alerts_oblasts_v2", resolved);
    }
  }

  const oblasts: OblastAlert[] =
    resolved?.oblasts ??
    OBLAST_CATALOG.map((o) => ({ uid: o.uid, code: o.code, name: o.name, level: "none" }));
  const source: AlertsPayload["source"] = resolved?.source ?? "unconfigured";
  const selected = oblasts.find((o) => o.uid === selectedUid) ?? oblasts[13];

  return {
    source,
    updatedAt: new Date().toISOString(),
    selectedUid: selected.uid,
    selected,
    oblasts
  };
}
