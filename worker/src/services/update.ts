import { readCache, writeCache } from "../cache";
import type { Env, UpdatePayload } from "../types";

interface GithubReleaseResponse {
  tag_name?: string;
  body?: string;
  published_at?: string;
  assets?: Array<{
    name: string;
    browser_download_url: string;
  }>;
}

const GITHUB_REPO = "voxquietam/voxdock";

export async function getLatestUpdate(env: Env): Promise<UpdatePayload | null> {
  const cached = await readCache<UpdatePayload | { empty: true }>(env.DB, "github_latest_release", 900);
  if (cached) {
    return "empty" in cached ? null : cached;
  }

  try {
    const res = await fetch(`https://api.github.com/repos/${GITHUB_REPO}/releases/latest`, {
      headers: {
        "User-Agent": "VoxDock-Worker/0.2",
        Accept: "application/vnd.github+json"
      }
    });
    if (!res.ok) {
      await writeCache(env.DB, "github_latest_release", { empty: true });
      return null;
    }

    const rel = (await res.json()) as GithubReleaseResponse;
    const apk = rel.assets?.find((a) => a.name.toLowerCase().endsWith(".apk"));
    if (!apk || !rel.tag_name) {
      await writeCache(env.DB, "github_latest_release", { empty: true });
      return null;
    }

    const payload: UpdatePayload = {
      version: rel.tag_name.replace(/^v/i, "").trim(),
      apkUrl: apk.browser_download_url,
      notes: (rel.body ?? "").trim().slice(0, 240),
      publishedAt: rel.published_at ?? new Date().toISOString()
    };
    await writeCache(env.DB, "github_latest_release", payload);
    return payload;
  } catch {
    return null;
  }
}
