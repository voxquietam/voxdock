export interface Env {
  DB: D1Database;
  VOXDOCK_TOKEN: string;
  ALERTS_IN_UA_TOKEN?: string;
  ACTA_MCP_URL?: string;
  ACTA_AUTH_HEADER?: string;
  DEFAULT_LAT: string;
  DEFAULT_LON: string;
  DEFAULT_CITY: string;
  DEFAULT_OBLAST_UID: string;
}

export type AlertLevel = "none" | "partial" | "active";

export interface OblastAlert {
  uid: number;
  code: string;
  name: string;
  level: AlertLevel;
}

export interface AlertsPayload {
  source: "alerts.in.ua" | "ubilling" | "unconfigured";
  updatedAt: string;
  selectedUid: number;
  selected: OblastAlert;
  oblasts: OblastAlert[];
}

export interface DailyForecast {
  date: string;
  tempMin: number;
  tempMax: number;
  precipProb: number;
  windMax: number;
  weatherCode: number;
}

export interface WeatherPayload {
  city: string;
  lat: number;
  lon: number;
  updatedAt: string;
  current: {
    temp: number;
    feelsLike: number;
    windSpeed: number;
    weatherCode: number;
    seaTemp: number | null;
    waveHeight: number | null;
    sunrise: string | null;
    sunset: string | null;
  };
  daily: DailyForecast[];
}

export interface CurrencyPayload {
  usdBuy: number;
  usdSell: number;
  eurBuy: number;
  eurSell: number;
  updatedAt: string;
}

export interface SensorsPayload {
  aqi: number;
  pm25: number;
  pm10: number;
  dust: number;
  label: string;
  updatedAt: string;
}

export interface ActaTaskSummary {
  id: string;
  identifier: string;
  title: string;
  status: string;
  priority: string;
  url: string;
  project: string;
  assignee: string;
  labels: string[];
}

export interface ActaPayload {
  configured: boolean;
  count: number;
  tasks: ActaTaskSummary[];
  updatedAt: string;
}

export type UnseenCategory = "sea" | "air" | "quake" | "space" | "bio";

export interface UnseenFinding {
  id: string;
  category: UnseenCategory;
  title: string;
  explanation: string;
  score: number;
  metric: string;
  observedAt: string;
}

export interface PowerSlot {
  startHour: number;
  endHour: number;
  state: "on" | "off" | "possible";
}

export interface PowerPayload {
  city: string;
  street: string;
  house: string;
  queueGroup: string;
  status: "on" | "off" | "possible" | "unconfigured";
  summary: string;
  nextChange: string | null;
  slots: PowerSlot[];
  updatedAt: string;
}

export interface NoteItem {
  id: number;
  kind: "note" | "reminder";
  text: string;
  remindAt: string | null;
  pinned: boolean;
  source: string;
  createdAt: string;
}

export interface AgentStatus {
  level: "ok" | "busy" | "warn" | "error";
  message: string;
  source: string;
  updatedAt: string;
}

export interface UpdatePayload {
  version: string;
  apkUrl: string;
  notes: string;
  publishedAt: string;
}
