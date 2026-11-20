import { readCache, writeCache } from "../cache";
import type { CurrencyPayload, Env } from "../types";

interface MonoRate {
  currencyCodeA: number;
  currencyCodeB: number;
  rateBuy?: number;
  rateSell?: number;
  rateCross?: number;
}

export async function getCurrency(env: Env): Promise<CurrencyPayload | null> {
  const cached = await readCache<CurrencyPayload>(env.DB, "monobank_fx", 1800);
  if (cached) return cached;

  try {
    const res = await fetch("https://api.monobank.ua/bank/currency", {
      headers: { "User-Agent": "VoxDock/0.2" }
    });
    if (!res.ok) return null;
    const rows = (await res.json()) as MonoRate[];
    const usd = rows.find((r) => r.currencyCodeA === 840 && r.currencyCodeB === 980);
    const eur = rows.find((r) => r.currencyCodeA === 978 && r.currencyCodeB === 980);
    if (!usd || !eur) return null;

    const payload: CurrencyPayload = {
      usdBuy: Math.round((usd.rateBuy ?? usd.rateCross ?? 0) * 100) / 100,
      usdSell: Math.round((usd.rateSell ?? usd.rateCross ?? 0) * 100) / 100,
      eurBuy: Math.round((eur.rateBuy ?? eur.rateCross ?? 0) * 100) / 100,
      eurSell: Math.round((eur.rateSell ?? eur.rateCross ?? 0) * 100) / 100,
      updatedAt: new Date().toISOString()
    };
    await writeCache(env.DB, "monobank_fx", payload);
    return payload;
  } catch {
    return null;
  }
}
