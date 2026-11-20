import { readCache, writeCache } from "../cache";
import type { Env, UnseenCategory, UnseenFinding } from "../types";

// Round coordinates to 2 decimal places (~1 km) before outbound requests for privacy.
function roundCoord(v: number): number {
  return Math.round(v * 100) / 100;
}

async function checkSeaUpwelling(lat: number, lon: number): Promise<UnseenFinding | null> {
  try {
    const url =
      `https://marine-api.open-meteo.com/v1/marine?latitude=${lat}&longitude=${lon}` +
      `&hourly=sea_surface_temperature&past_days=4&forecast_days=1`;
    const res = await fetch(url, { headers: { "User-Agent": "VoxDock/0.1" } });
    if (!res.ok) return null;
    const data = (await res.json()) as {
      hourly?: { sea_surface_temperature?: Array<number | null> };
    };
    const vals = (data.hourly?.sea_surface_temperature ?? []).filter(
      (v): v is number => typeof v === "number"
    );
    if (vals.length < 48) return null;

    const latest = vals[vals.length - 1];
    const earlier = vals[Math.max(0, vals.length - 72)];
    const delta = Math.round((latest - earlier) * 10) / 10;

    if (Math.abs(delta) < 1.5) return null;
    const score = Math.min(3.0, Math.round((Math.abs(delta) / 2.0) * 10) / 10);
    const cooling = delta < 0;

    return {
      id: `sea_${new Date().toISOString().slice(0, 10)}`,
      category: "sea",
      title: cooling
        ? `Coastal upwelling: sea dropped ${delta}°C`
        : `Warm coastal surge: sea rose +${delta}°C`,
      explanation: cooling
        ? `Offshore wind pushed warm surface water away, pulling cold deep water (${latest.toFixed(1)}°C) to the shore.`
        : `Surface water warmed rapidly to ${latest.toFixed(1)}°C over the last 3 days.`,
      score,
      metric: `${latest.toFixed(1)}°C`,
      observedAt: new Date().toISOString()
    };
  } catch {
    return null;
  }
}

async function checkAirAnomaly(lat: number, lon: number): Promise<UnseenFinding | null> {
  try {
    const url =
      `https://air-quality-api.open-meteo.com/v1/air-quality?latitude=${lat}&longitude=${lon}` +
      `&hourly=dust,pm2_5&past_days=31&forecast_days=1`;
    const res = await fetch(url, { headers: { "User-Agent": "VoxDock/0.1" } });
    if (!res.ok) return null;
    const data = (await res.json()) as {
      hourly?: { dust?: Array<number | null>; pm2_5?: Array<number | null> };
    };

    const dust = (data.hourly?.dust ?? []).filter((v): v is number => typeof v === "number");
    if (dust.length > 48) {
      const history = dust.slice(0, -24);
      const recent = dust.slice(-24);
      const avg = history.reduce((a, b) => a + b, 0) / Math.max(1, history.length);
      const maxRecent = Math.max(...recent);
      if (maxRecent >= 25 && avg > 0.5) {
        const ratio = maxRecent / avg;
        if (ratio >= 2.2) {
          const score = Math.min(3.0, Math.round(Math.log2(ratio) * 10) / 10);
          return {
            id: `dust_${new Date().toISOString().slice(0, 10)}`,
            category: "air",
            title: `Saharan dust plume: ${ratio.toFixed(1)}x normal`,
            explanation: `Mineral dust peaked at ${Math.round(maxRecent)} ug/m3 vs 30-day baseline ${Math.round(avg)}. Expect hazy copper sunsets.`,
            score,
            metric: `${Math.round(maxRecent)} ug/m3`,
            observedAt: new Date().toISOString()
          };
        }
      }
    }

    const pm = (data.hourly?.pm2_5 ?? []).filter((v): v is number => typeof v === "number");
    if (pm.length > 48) {
      const history = pm.slice(0, -24);
      const recent = pm.slice(-24);
      const avg = history.reduce((a, b) => a + b, 0) / Math.max(1, history.length);
      const maxRecent = Math.max(...recent);
      if (maxRecent >= 25 && avg > 1) {
        const ratio = maxRecent / avg;
        if (ratio >= 2.5) {
          const score = Math.min(3.0, Math.round(Math.log2(ratio) * 10) / 10);
          return {
            id: `pm25_${new Date().toISOString().slice(0, 10)}`,
            category: "air",
            title: `PM2.5 spike: ${ratio.toFixed(1)}x local baseline`,
            explanation: `Fine particulates reached ${Math.round(maxRecent)} ug/m3 against a 30-day average of ${Math.round(avg)}.`,
            score,
            metric: `${Math.round(maxRecent)} ug/m3`,
            observedAt: new Date().toISOString()
          };
        }
      }
    }

    return null;
  } catch {
    return null;
  }
}

async function checkEarthquakes(lat: number, lon: number): Promise<UnseenFinding | null> {
  try {
    const start = new Date(Date.now() - 72 * 3600 * 1000).toISOString();
    const url =
      `https://earthquake.usgs.gov/fdsnws/event/1/query?format=geojson` +
      `&starttime=${encodeURIComponent(start)}&minmagnitude=3.2` +
      `&latitude=${lat}&longitude=${lon}&maxradiuskm=1200&orderby=magnitude&limit=1`;
    const res = await fetch(url, { headers: { "User-Agent": "VoxDock/0.1" } });
    if (!res.ok) return null;
    const data = (await res.json()) as {
      features?: Array<{
        id: string;
        properties: { mag: number; place: string; time: number };
        geometry: { coordinates: [number, number, number] };
      }>;
    };
    const eq = data.features?.[0];
    if (!eq) return null;

    const mag = eq.properties.mag;
    const depthKm = Math.round(eq.geometry.coordinates[2] ?? 0);
    const score = Math.min(3.0, Math.round(Math.max(1.0, (mag - 2.5) * 1.1) * 10) / 10);

    return {
      id: `quake_${eq.id}`,
      category: "quake",
      title: `Quake M${mag.toFixed(1)} — ${eq.properties.place}`,
      explanation: `Depth ${depthKm} km. Deep Vrancea/regional tremors above M4.0 can be subtly felt on upper floors.`,
      score,
      metric: `M${mag.toFixed(1)}`,
      observedAt: new Date(eq.properties.time).toISOString()
    };
  } catch {
    return null;
  }
}

async function checkGeomagnetic(): Promise<UnseenFinding | null> {
  try {
    const res = await fetch(
      "https://services.swpc.noaa.gov/products/noaa-planetary-k-index-forecast.json",
      { headers: { "User-Agent": "VoxDock/0.1" } }
    );
    if (!res.ok) return null;
    const rows = (await res.json()) as string[][];
    let maxKp = 0;
    for (let i = 1; i < rows.length; i++) {
      const kp = Number(rows[i]?.[1] ?? 0);
      if (kp > maxKp) maxKp = kp;
    }
    if (maxKp < 4.5) return null;

    const score = Math.min(3.0, Math.round(((maxKp - 3.5) / 1.5) * 10) / 10);
    return {
      id: `kp_${new Date().toISOString().slice(0, 10)}`,
      category: "space",
      title: `Geomagnetic activity Kp ${maxKp.toFixed(1)}`,
      explanation:
        maxKp >= 6
          ? "Strong G2+ geomagnetic storm — faint red aurora may be visible low on the northern horizon."
          : "Elevated solar wind hitting Earth's magnetosphere over the next 24 hours.",
      score,
      metric: `Kp ${maxKp.toFixed(1)}`,
      observedAt: new Date().toISOString()
    };
  } catch {
    return null;
  }
}

async function checkNearbyBio(lat: number, lon: number): Promise<UnseenFinding | null> {
  try {
    const url =
      `https://api.inaturalist.org/v1/observations?lat=${lat}&lng=${lon}&radius=5` +
      `&quality_grade=research&order=desc&order_by=created_at&per_page=10`;
    const res = await fetch(url, { headers: { "User-Agent": "VoxDock/0.1" } });
    if (!res.ok) return null;
    const data = (await res.json()) as {
      results?: Array<{
        id: number;
        observed_on?: string;
        taxon?: {
          name: string;
          preferred_common_name?: string;
          observations_count?: number;
        };
      }>;
    };

    const candidates = (data.results ?? []).filter(
      (r) => r.taxon && typeof r.taxon.observations_count === "number"
    );
    if (candidates.length === 0) return null;

    candidates.sort(
      (a, b) => (a.taxon?.observations_count ?? 1e9) - (b.taxon?.observations_count ?? 1e9)
    );
    const rarest = candidates[0];
    const count = rarest.taxon?.observations_count ?? 10000;
    const label = rarest.taxon?.preferred_common_name || rarest.taxon?.name || "Unknown species";
    const score = Math.min(3.0, Math.max(0.8, Math.round((4.5 - Math.log10(Math.max(10, count))) * 10) / 10));

    return {
      id: `bio_${rarest.id}`,
      category: "bio",
      title: `Nearby sighting: ${label}`,
      explanation: `Photographed within 5 km (${count.toLocaleString()} global research-grade records on iNaturalist).`,
      score,
      metric: `${count} global`,
      observedAt: rarest.observed_on ?? new Date().toISOString()
    };
  } catch {
    return null;
  }
}

export async function refreshUnseen(
  env: Env,
  rawLat: number,
  rawLon: number
): Promise<UnseenFinding[]> {
  const lat = roundCoord(rawLat);
  const lon = roundCoord(rawLon);
  const key = `unseen_${lat.toFixed(2)}_${lon.toFixed(2)}`;

  const results = await Promise.all([
    checkSeaUpwelling(lat, lon),
    checkAirAnomaly(lat, lon),
    checkEarthquakes(lat, lon),
    checkGeomagnetic(),
    checkNearbyBio(lat, lon)
  ]);

  const findings = results
    .filter((f): f is UnseenFinding => f !== null)
    .sort((a, b) => b.score - a.score);

  await writeCache(env.DB, key, findings);
  return findings;
}

export async function getUnseen(
  env: Env,
  rawLat: number,
  rawLon: number,
  enabled?: Set<UnseenCategory>
): Promise<UnseenFinding[]> {
  const lat = roundCoord(rawLat);
  const lon = roundCoord(rawLon);
  const key = `unseen_${lat.toFixed(2)}_${lon.toFixed(2)}`;

  let findings = await readCache<UnseenFinding[]>(env.DB, key, 7200);
  if (!findings) {
    findings = await refreshUnseen(env, lat, lon);
  }
  if (enabled && enabled.size > 0) {
    return findings.filter((f) => enabled.has(f.category));
  }
  return findings;
}
