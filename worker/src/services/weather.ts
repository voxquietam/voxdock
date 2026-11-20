import { readCache, writeCache } from "../cache";
import type { DailyForecast, Env, WeatherPayload } from "../types";

interface OpenMeteoForecastResponse {
  current?: {
    temperature_2m: number;
    apparent_temperature: number;
    wind_speed_10m: number;
    weather_code: number;
  };
  daily?: {
    time: string[];
    temperature_2m_min: number[];
    temperature_2m_max: number[];
    precipitation_probability_max: number[];
    wind_speed_10m_max: number[];
    weather_code: number[];
    sunrise?: string[];
    sunset?: string[];
  };
}

interface OpenMeteoMarineResponse {
  current?: {
    sea_surface_temperature?: number | null;
    wave_height?: number | null;
  };
}

export async function getWeather(
  env: Env,
  lat: number,
  lon: number,
  city: string
): Promise<WeatherPayload> {
  const key = `weather_v2_${lat.toFixed(2)}_${lon.toFixed(2)}`;
  const cached = await readCache<WeatherPayload>(env.DB, key, 1200);
  if (cached) return cached;

  const forecastUrl =
    `https://api.open-meteo.com/v1/forecast?latitude=${lat}&longitude=${lon}` +
    `&current=temperature_2m,apparent_temperature,wind_speed_10m,weather_code` +
    `&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,wind_speed_10m_max,sunrise,sunset` +
    `&forecast_days=10&timezone=auto`;

  const marineUrl =
    `https://marine-api.open-meteo.com/v1/marine?latitude=${lat}&longitude=${lon}` +
    `&current=sea_surface_temperature,wave_height`;

  const [forecastRes, marineRes] = await Promise.all([
    fetch(forecastUrl, { headers: { "User-Agent": "VoxDock/0.2" } }),
    fetch(marineUrl, { headers: { "User-Agent": "VoxDock/0.2" } }).catch(() => null)
  ]);

  if (!forecastRes.ok) {
    throw new Error(`Open-Meteo forecast error: ${forecastRes.status}`);
  }

  const forecast = (await forecastRes.json()) as OpenMeteoForecastResponse;
  let seaTemp: number | null = null;
  let waveHeight: number | null = null;
  if (marineRes && marineRes.ok) {
    const marine = (await marineRes.json()) as OpenMeteoMarineResponse;
    if (typeof marine.current?.sea_surface_temperature === "number") {
      seaTemp = Math.round(marine.current.sea_surface_temperature * 10) / 10;
    }
    if (typeof marine.current?.wave_height === "number") {
      waveHeight = Math.round(marine.current.wave_height * 100) / 100;
    }
  }

  const daily: DailyForecast[] = [];
  if (forecast.daily) {
    const len = Math.min(10, forecast.daily.time.length);
    for (let i = 0; i < len; i++) {
      daily.push({
        date: forecast.daily.time[i],
        tempMin: Math.round(forecast.daily.temperature_2m_min[i]),
        tempMax: Math.round(forecast.daily.temperature_2m_max[i]),
        precipProb: Math.round(forecast.daily.precipitation_probability_max[i] ?? 0),
        windMax: Math.round(forecast.daily.wind_speed_10m_max[i] ?? 0),
        weatherCode: forecast.daily.weather_code[i] ?? 0
      });
    }
  }

  const payload: WeatherPayload = {
    city,
    lat,
    lon,
    updatedAt: new Date().toISOString(),
    current: {
      temp: Math.round(forecast.current?.temperature_2m ?? 0),
      feelsLike: Math.round(forecast.current?.apparent_temperature ?? 0),
      windSpeed: Math.round(forecast.current?.wind_speed_10m ?? 0),
      weatherCode: forecast.current?.weather_code ?? 0,
      seaTemp,
      waveHeight,
      sunrise: forecast.daily?.sunrise?.[0] ?? null,
      sunset: forecast.daily?.sunset?.[0] ?? null
    },
    daily
  };

  await writeCache(env.DB, key, payload);
  return payload;
}
