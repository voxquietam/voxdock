import { readCache, writeCache } from "../cache";
import type { Env, SensorsPayload } from "../types";

interface AirQualityCurrentResponse {
  current?: {
    european_aqi?: number;
    pm2_5?: number;
    pm10?: number;
    dust?: number;
  };
}

function aqiLabel(aqi: number): string {
  if (aqi <= 20) return "GOOD";
  if (aqi <= 40) return "FAIR";
  if (aqi <= 60) return "MODERATE";
  if (aqi <= 80) return "POOR";
  return "VERY POOR";
}

export async function getSensors(env: Env, lat: number, lon: number): Promise<SensorsPayload | null> {
  const key = `sensors_${lat.toFixed(2)}_${lon.toFixed(2)}`;
  const cached = await readCache<SensorsPayload>(env.DB, key, 1200);
  if (cached) return cached;

  try {
    const url =
      `https://air-quality-api.open-meteo.com/v1/air-quality?latitude=${lat}&longitude=${lon}` +
      `&current=european_aqi,pm2_5,pm10,dust`;
    const res = await fetch(url, { headers: { "User-Agent": "VoxDock/0.2" } });
    if (!res.ok) return null;
    const data = (await res.json()) as AirQualityCurrentResponse;
    const c = data.current;
    if (!c) return null;

    const aqi = Math.round(c.european_aqi ?? 0);
    const payload: SensorsPayload = {
      aqi,
      pm25: Math.round((c.pm2_5 ?? 0) * 10) / 10,
      pm10: Math.round((c.pm10 ?? 0) * 10) / 10,
      dust: Math.round(c.dust ?? 0),
      label: aqiLabel(aqi),
      updatedAt: new Date().toISOString()
    };
    await writeCache(env.DB, key, payload);
    return payload;
  } catch {
    return null;
  }
}
