import { readCache, writeCache } from "../cache";
import type { Env, UpdatePayload } from "../types";

const GITHUB_REPO = "voxquietam/voxdock";
const APK_ASSET_NAME = "app-debug.apk";

export async function getLatestUpdate(env: Env): Promise<UpdatePayload | null> {
  const cached = await readCache<UpdatePayload>(env.DB, "github_latest_release_v2", 300);
  if (cached) return cached;

  try {
    // Use github.com redirect instead of api.github.com to avoid shared Cloudflare egress IP rate limits
    const res = await fetch(`https://github.com/${GITHUB_REPO}/releases/latest`, {
      method: "HEAD",
      redirect: "manual",
      headers: { "User-Agent": "VoxDock-Worker/0.2" }
    });

    const location = res.headers.get("location") ?? "";
    const match = location.match(/\/releases\/tag\/([^/?#]+)/);
    if (!match) return null;

    const tag = match[1].trim();
    const version = tag.replace(/^v/i, "");
    const payload: UpdatePayload = {
      version,
      apkUrl: `https://github.com/${GITHUB_REPO}/releases/download/${tag}/${APK_ASSET_NAME}`,
      notes: `Release ${tag}`,
      publishedAt: new Date().toISOString()
    };

    await writeCache(env.DB, "github_latest_release_v2", payload);
    return payload;
  } catch {
    return null;
  }
}
