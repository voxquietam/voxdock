import { readCache, writeCache } from "../cache";
import type { ActaPayload, ActaTaskSummary, Env } from "../types";

function parseMcpTextResponse(rawText: string): unknown {
  const trimmed = rawText.trim();
  if (trimmed.startsWith("{")) {
    return JSON.parse(trimmed);
  }
  const dataLines = trimmed
    .split("\n")
    .filter((l) => l.startsWith("data:"))
    .map((l) => l.slice(5).trim())
    .filter(Boolean);
  for (let i = dataLines.length - 1; i >= 0; i--) {
    try {
      return JSON.parse(dataLines[i]);
    } catch {
      // continue
    }
  }
  return null;
}

export async function getActaTasks(env: Env): Promise<ActaPayload> {
  const url = env.ACTA_MCP_URL?.trim();
  const auth = env.ACTA_AUTH_HEADER?.trim();
  if (!url || !auth) {
    return {
      configured: false,
      count: 0,
      tasks: [],
      updatedAt: new Date().toISOString()
    };
  }

  const cached = await readCache<ActaPayload>(env.DB, "acta_tasks_v2", 600);
  if (cached) return cached;

  try {
    const res = await fetch(url, {
      method: "POST",
      headers: {
        Authorization: auth,
        "Content-Type": "application/json",
        Accept: "application/json, text/event-stream",
        "User-Agent": "VoxDock/0.2"
      },
      body: JSON.stringify({
        jsonrpc: "2.0",
        id: 1,
        method: "tools/call",
        params: {
          name: "acta_tasks_list",
          arguments: { limit: 20 }
        }
      })
    });

    if (!res.ok) {
      return {
        configured: true,
        count: 0,
        tasks: [],
        updatedAt: new Date().toISOString()
      };
    }

    const parsed = parseMcpTextResponse(await res.text()) as {
      result?: { content?: Array<{ type: string; text?: string }> };
    } | null;

    const textBlock = parsed?.result?.content?.find((c) => c.type === "text")?.text ?? "";
    const tasks: ActaTaskSummary[] = [];

    if (textBlock) {
      try {
        const inner = JSON.parse(textBlock) as
          | Array<Record<string, unknown>>
          | { tasks?: Array<Record<string, unknown>>; items?: Array<Record<string, unknown>> };
        const arr = Array.isArray(inner) ? inner : inner.tasks ?? inner.items ?? [];
        for (const t of arr) {
          const status = String(t.status ?? t.state ?? "open");
          const lowerSt = status.toLowerCase();
          if (lowerSt.includes("done") || lowerSt.includes("cancel") || lowerSt.includes("archive")) {
            continue;
          }
          const rawLabels = Array.isArray(t.labels) ? t.labels : [];
          const labels = rawLabels
            .map((l) => (typeof l === "string" ? l : String((l as Record<string, unknown>).name ?? "")))
            .filter(Boolean);
          const slug = String(t.slug ?? t.identifier ?? t.key ?? "");
          tasks.push({
            id: String(t.id ?? slug),
            identifier: slug,
            title: String(t.title ?? t.name ?? "Untitled"),
            status,
            priority: String(t.priority ?? ""),
            url: String(t.url ?? ""),
            project: String(t.project_name ?? t.project_slug_prefix ?? ""),
            assignee: String(t.assignee_username ?? ""),
            labels
          });
        }
      } catch {
        // Ignore unparseable response
      }
    }

    const payload: ActaPayload = {
      configured: true,
      count: tasks.length,
      tasks: tasks.slice(0, 15),
      updatedAt: new Date().toISOString()
    };
    await writeCache(env.DB, "acta_tasks_v2", payload);
    return payload;
  } catch {
    return {
      configured: true,
      count: 0,
      tasks: [],
      updatedAt: new Date().toISOString()
    };
  }
}
