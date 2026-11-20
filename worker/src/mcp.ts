import { jsonResponse } from "./auth";
import { savePowerSchedule } from "./services/power";
import type { Env, PowerSlot } from "./types";

interface JsonRpcRequest {
  jsonrpc: "2.0";
  id?: string | number | null;
  method: string;
  params?: Record<string, unknown>;
}

const TOOLS = [
  {
    name: "push_note",
    description: "Push a note card to the VoxDock Android screen.",
    inputSchema: {
      type: "object",
      properties: {
        text: { type: "string", description: "Short note text to display." },
        pinned: { type: "boolean", description: "Pin note at the top of the card." },
        source: { type: "string", description: "Agent or tool name (e.g. claude, antigravity)." }
      },
      required: ["text"]
    }
  },
  {
    name: "push_reminder",
    description: "Push a timed reminder to VoxDock (e.g. '18:00' or ISO timestamp).",
    inputSchema: {
      type: "object",
      properties: {
        text: { type: "string", description: "Reminder message." },
        remind_at: { type: "string", description: "Time string (HH:MM or ISO-8601)." },
        source: { type: "string", description: "Agent name." }
      },
      required: ["text", "remind_at"]
    }
  },
  {
    name: "set_agent_status",
    description: "Update the live agent status strip on VoxDock.",
    inputSchema: {
      type: "object",
      properties: {
        level: {
          type: "string",
          enum: ["ok", "busy", "warn", "error"],
          description: "Status indicator color."
        },
        message: { type: "string", description: "One-line status text." },
        source: { type: "string", description: "Agent name." }
      },
      required: ["level", "message"]
    }
  },
  {
    name: "list_notes",
    description: "List active (non-dismissed) notes and reminders on VoxDock.",
    inputSchema: { type: "object", properties: {} }
  },
  {
    name: "update_power_schedule",
    description: "Update today's DTEK power outage slots for a queue group.",
    inputSchema: {
      type: "object",
      properties: {
        queue_group: { type: "string", description: "Queue group (e.g. '3.1', '4.2')." },
        summary: { type: "string", description: "Short human-readable schedule summary." },
        slots: {
          type: "array",
          items: {
            type: "object",
            properties: {
              startHour: { type: "number" },
              endHour: { type: "number" },
              state: { type: "string", enum: ["on", "off", "possible"] }
            },
            required: ["startHour", "endHour", "state"]
          }
        }
      },
      required: ["queue_group", "summary", "slots"]
    }
  }
];

function rpcOk(id: string | number | null | undefined, result: unknown): Response {
  return jsonResponse({ jsonrpc: "2.0", id: id ?? null, result });
}

function rpcErr(
  id: string | number | null | undefined,
  code: number,
  message: string
): Response {
  return jsonResponse({ jsonrpc: "2.0", id: id ?? null, error: { code, message } });
}

export async function handleMcpRequest(request: Request, env: Env): Promise<Response> {
  if (request.method !== "POST") {
    return jsonResponse({ error: "Use POST for MCP JSON-RPC" }, 405);
  }

  let body: JsonRpcRequest;
  try {
    body = (await request.json()) as JsonRpcRequest;
  } catch {
    return rpcErr(null, -32700, "Parse error");
  }

  if (body.method === "initialize") {
    return rpcOk(body.id, {
      protocolVersion: "2024-11-05",
      capabilities: { tools: {} },
      serverInfo: { name: "voxdock-mcp", version: "0.1.0" }
    });
  }

  if (body.method === "notifications/initialized") {
    return new Response(null, { status: 202 });
  }

  if (body.method === "tools/list") {
    return rpcOk(body.id, { tools: TOOLS });
  }

  if (body.method === "tools/call") {
    const name = String(body.params?.name ?? "");
    const args = (body.params?.arguments ?? {}) as Record<string, unknown>;

    if (name === "push_note") {
      const text = String(args.text ?? "").trim();
      if (!text) return rpcErr(body.id, -32602, "text is required");
      const pinned = args.pinned ? 1 : 0;
      const source = String(args.source ?? "mcp");
      const res = await env.DB.prepare(
        "INSERT INTO notes (kind, text, pinned, source) VALUES ('note', ?, ?, ?)"
      )
        .bind(text, pinned, source)
        .run();
      return rpcOk(body.id, {
        content: [{ type: "text", text: `Note #${res.meta.last_row_id} pushed to VoxDock.` }]
      });
    }

    if (name === "push_reminder") {
      const text = String(args.text ?? "").trim();
      const remindAt = String(args.remind_at ?? "").trim();
      if (!text || !remindAt) {
        return rpcErr(body.id, -32602, "text and remind_at are required");
      }
      const source = String(args.source ?? "mcp");
      const res = await env.DB.prepare(
        "INSERT INTO notes (kind, text, remind_at, pinned, source) VALUES ('reminder', ?, ?, 1, ?)"
      )
        .bind(text, remindAt, source)
        .run();
      return rpcOk(body.id, {
        content: [
          {
            type: "text",
            text: `Reminder #${res.meta.last_row_id} set for ${remindAt}.`
          }
        ]
      });
    }

    if (name === "set_agent_status") {
      const level = String(args.level ?? "ok");
      const message = String(args.message ?? "").trim();
      const source = String(args.source ?? "mcp");
      if (!message) return rpcErr(body.id, -32602, "message is required");
      await env.DB.prepare(
        "UPDATE agent_status SET level = ?, message = ?, source = ?, updated_at = datetime('now') WHERE id = 1"
      )
        .bind(level, message, source)
        .run();
      return rpcOk(body.id, {
        content: [{ type: "text", text: `Agent status updated: [${level}] ${message}` }]
      });
    }

    if (name === "list_notes") {
      const { results } = await env.DB.prepare(
        "SELECT id, kind, text, remind_at, pinned, source, created_at FROM notes WHERE dismissed = 0 ORDER BY pinned DESC, id DESC LIMIT 20"
      ).all();
      return rpcOk(body.id, {
        content: [{ type: "text", text: JSON.stringify(results ?? [], null, 2) }]
      });
    }

    if (name === "update_power_schedule") {
      const queueGroup = String(args.queue_group ?? "").trim();
      const summary = String(args.summary ?? "").trim();
      const slots = (args.slots ?? []) as PowerSlot[];
      if (!queueGroup) return rpcErr(body.id, -32602, "queue_group is required");
      await savePowerSchedule(env.DB, queueGroup, slots, summary);
      return rpcOk(body.id, {
        content: [{ type: "text", text: `Power schedule updated for queue ${queueGroup}.` }]
      });
    }

    return rpcErr(body.id, -32601, `Unknown tool: ${name}`);
  }

  return rpcErr(body.id, -32601, `Method not found: ${body.method}`);
}
