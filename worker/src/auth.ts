import type { Env } from "./types";

export function jsonResponse(data: unknown, status = 200): Response {
  return new Response(JSON.stringify(data), {
    status,
    headers: {
      "content-type": "application/json; charset=utf-8",
      "access-control-allow-origin": "*",
      "access-control-allow-headers": "authorization, content-type",
      "access-control-allow-methods": "GET, POST, DELETE, OPTIONS"
    }
  });
}

export function isAuthorized(request: Request, env: Env): boolean {
  const expected = env.VOXDOCK_TOKEN?.trim();
  if (!expected) {
    // Allow local dev when no secret is configured yet
    return true;
  }
  const auth = request.headers.get("authorization") ?? "";
  if (auth === `Bearer ${expected}`) {
    return true;
  }
  const url = new URL(request.url);
  return url.searchParams.get("token") === expected;
}
