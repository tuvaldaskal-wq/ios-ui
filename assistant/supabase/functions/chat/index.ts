// Aria chat proxy — Supabase Edge Function (Deno).
//
// The Android app calls this instead of Anthropic directly. This function holds
// the Anthropic key (server-side env), identifies the signed-in user from their
// JWT, calls Claude, logs the turn + token cost, and returns Claude's response.
//
// Deploy:  supabase functions deploy chat --no-verify-jwt
// Secrets: supabase secrets set ANTHROPIC_API_KEY=sk-ant-...
//          (SUPABASE_URL and SUPABASE_SERVICE_ROLE_KEY are provided automatically)

import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const cors = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

// Per-1M-token prices (USD). Extend as you add models.
const PRICES: Record<string, { in: number; out: number }> = {
  "claude-haiku-4-5": { in: 1, out: 5 },
  "claude-sonnet-4-6": { in: 3, out: 15 },
  "claude-opus-4-8": { in: 5, out: 25 },
};

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: cors });

  const ANTHROPIC_API_KEY = Deno.env.get("ANTHROPIC_API_KEY") ?? "";
  const SUPABASE_URL = Deno.env.get("SUPABASE_URL")!;
  const SERVICE_ROLE = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;

  const admin = createClient(SUPABASE_URL, SERVICE_ROLE);

  // Identify the signed-in user from the bearer token.
  const auth = req.headers.get("Authorization") ?? "";
  const jwt = auth.replace(/^Bearer\s+/i, "");
  const { data: userData } = await admin.auth.getUser(jwt);
  const user = userData?.user;
  if (!user) {
    return json({ error: { message: "Not signed in." } }, 401);
  }

  // OPTIONAL: gate by plan. Uncomment to require a paid plan.
  // const { data: profile } = await admin.from("profiles")
  //   .select("plan").eq("id", user.id).single();
  // if (!profile || profile.plan === "free") {
  //   return json({ error: { message: "No active plan." } }, 402);
  // }

  let body: any;
  try {
    body = await req.json();
  } catch {
    return json({ error: { message: "Bad request body." } }, 400);
  }
  const model: string = body.model ?? "claude-haiku-4-5";

  // Call Anthropic with the server-held key.
  const anthropic = await fetch("https://api.anthropic.com/v1/messages", {
    method: "POST",
    headers: {
      "content-type": "application/json",
      "x-api-key": ANTHROPIC_API_KEY,
      "anthropic-version": "2023-06-01",
    },
    body: JSON.stringify(body),
  });
  const result = await anthropic.json();

  // Log usage + the latest turn (best-effort; never block the response).
  try {
    const usage = result?.usage ?? {};
    const inTok = usage.input_tokens ?? 0;
    const outTok = usage.output_tokens ?? 0;
    const p = PRICES[model] ?? { in: 0, out: 0 };
    const cost = (inTok / 1e6) * p.in + (outTok / 1e6) * p.out;
    await admin.from("usage").insert({
      user_id: user.id, model, input_tokens: inTok,
      output_tokens: outTok, cost_usd: cost,
    });

    const lastUser = [...(body.messages ?? [])].reverse()
      .find((m: any) => m.role === "user" && typeof m.content === "string");
    if (lastUser) {
      await admin.from("messages").insert({
        user_id: user.id, role: "user", content: lastUser.content,
      });
    }
    const text = (result?.content ?? [])
      .filter((b: any) => b.type === "text").map((b: any) => b.text).join("");
    if (text) {
      await admin.from("messages").insert({
        user_id: user.id, role: "assistant", content: text,
      });
    }
  } catch (_e) {
    // logging is best-effort
  }

  return json(result, anthropic.status);
});

function json(obj: unknown, status = 200) {
  return new Response(JSON.stringify(obj), {
    status,
    headers: { ...cors, "content-type": "application/json" },
  });
}
