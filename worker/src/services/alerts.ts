import { readCache, writeCache } from "../cache";
import type { AlertLevel, AlertsPayload, Env, OblastAlert } from "../types";

// Official alerts.in.ua oblast order for /v1/iot/active_air_raid_alerts_by_oblast.json
// Index in the 27-char status string corresponds to this canonical list.
const OBLAST_CATALOG: ReadonlyArray<{ uid: number; code: string; name: string }> = [
  { uid: 3, code: "KM", name: "Khmelnytskyi" },
  { uid: 4, code: "VI", name: "Vinnytsia" },
  { uid: 5, code: "RV", name: "Rivne" },
  { uid: 8, code: "VO", name: "Volyn" },
  { uid: 9, code: "DP", name: "Dnipropetrovsk" },
  { uid: 10, code: "ZT", name: "Zhytomyr" },
  { uid: 11, code: "ZK", name: "Zakarpattia" },
  { uid: 12, code: "ZP", name: "Zaporizhzhia" },
  { uid: 13, code: "IF", name: "Ivano-Frankivsk" },
  { uid: 14, code: "KV", name: "Kyiv Oblast" },
  { uid: 15, code: "KR", name: "Kirovohrad" },
  { uid: 16, code: "LG", name: "Luhansk" },
  { uid: 17, code: "MK", name: "Mykolaiv" },
  { uid: 18, code: "OD", name: "Odesa" },
  { uid: 19, code: "PL", name: "Poltava" },
  { uid: 20, code: "SM", name: "Sumy" },
  { uid: 21, code: "TE", name: "Ternopil" },
  { uid: 22, code: "KH", name: "Kharkiv" },
  { uid: 23, code: "KS", name: "Kherson" },
  { uid: 24, code: "CK", name: "Cherkasy" },
  { uid: 25, code: "CH", name: "Chernihiv" },
  { uid: 26, code: "CV", name: "Chernivtsi" },
  { uid: 27, code: "LV", name: "Lviv" },
  { uid: 28, code: "DN", name: "Donetsk" },
  { uid: 29, code: "CR", name: "Crimea" },
  { uid: 30, code: "SV", name: "Sevastopol" },
  { uid: 31, code: "KC", name: "Kyiv City" }
];

function decodeChar(ch: string): AlertLevel {
  if (ch === "A") return "active";
  if (ch === "P") return "partial";
  return "none";
}

export async function getAlerts(env: Env, selectedUid: number): Promise<AlertsPayload> {
  const token = env.ALERTS_IN_UA_TOKEN?.trim();
  const fallbackOblasts: OblastAlert[] = OBLAST_CATALOG.map((o) => ({
    ...o,
    level: "none"
  }));

  if (!token) {
    const selected =
      fallbackOblasts.find((o) => o.uid === selectedUid) ?? fallbackOblasts[13];
    return {
      source: "unconfigured",
      updatedAt: new Date().toISOString(),
      selectedUid: selected.uid,
      selected,
      oblasts: fallbackOblasts
    };
  }

  const cached = await readCache<OblastAlert[]>(env.DB, "alerts_oblasts", 25);
  let oblasts = cached;

  if (!oblasts) {
    const res = await fetch(
      "https://api.alerts.in.ua/v1/iot/active_air_raid_alerts_by_oblast.json",
      {
        headers: {
          Authorization: `Bearer ${token}`,
          "User-Agent": "VoxDock/0.1"
        }
      }
    );

    if (res.ok) {
      const rawText = (await res.text()).replace(/^"|"$/g, "").trim();
      oblasts = OBLAST_CATALOG.map((o, idx) => ({
        ...o,
        level: decodeChar(rawText[idx] ?? "N")
      }));
      await writeCache(env.DB, "alerts_oblasts", oblasts);
    } else {
      oblasts = fallbackOblasts;
    }
  }

  const selected = oblasts.find((o) => o.uid === selectedUid) ?? oblasts[13];
  return {
    source: "alerts.in.ua",
    updatedAt: new Date().toISOString(),
    selectedUid: selected.uid,
    selected,
    oblasts
  };
}
