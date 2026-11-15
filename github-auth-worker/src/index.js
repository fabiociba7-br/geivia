/**
 * GEIVIA GitHub App OAuth callback + code exchange.
 *
 * Configure these Cloudflare Worker secrets/variables:
 *   GITHUB_CLIENT_ID      (plain variable)
 *   GITHUB_CLIENT_SECRET  (secret)
 *   GITHUB_CALLBACK_URL   (plain variable; exact HTTPS /callback URL)
 *
 * GitHub App user authorization callback URL must equal GITHUB_CALLBACK_URL.
 * Never commit the Client Secret or put it in the Android app.
 */
const APP_REDIRECT = "geivia://github/callback";
const JSON_HEADERS = {
  "content-type": "application/json; charset=utf-8",
  "cache-control": "no-store, max-age=0",
  "pragma": "no-cache"
};

function json(body, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: JSON_HEADERS });
}

export default {
  async fetch(request, env) {
    const url = new URL(request.url);

    if (request.method === "GET" && url.pathname === "/health") {
      return json({ service: "GEIVIA GitHub OAuth", status: "ok" });
    }

    if (request.method === "GET" && url.pathname === "/callback") {
      const code = url.searchParams.get("code");
      const state = url.searchParams.get("state");
      const error = url.searchParams.get("error");
      if (error) {
        const target = new URL(APP_REDIRECT);
        target.searchParams.set("error", error);
        if (state) target.searchParams.set("state", state);
        return Response.redirect(target.toString(), 302);
      }
      if (!code || !state || code.length > 2048 || state.length > 256) {
        return new Response("Callback GitHub inválido. Volte ao GEIVIA e tente novamente.", {
          status: 400,
          headers: { "content-type": "text/plain; charset=utf-8", "cache-control": "no-store" }
        });
      }
      const target = new URL(APP_REDIRECT);
      target.searchParams.set("code", code);
      target.searchParams.set("state", state);
      return Response.redirect(target.toString(), 302);
    }

    if (request.method === "POST" && url.pathname === "/exchange") {
      if (!env.GITHUB_CLIENT_ID || !env.GITHUB_CLIENT_SECRET || !env.GITHUB_CALLBACK_URL) {
        return json({ error: "Serviço ainda não configurado." }, 503);
      }

      let input;
      try {
        const raw = await request.text();
        if (raw.length > 16_384) return json({ error: "Requisição muito grande." }, 413);
        input = JSON.parse(raw);
      } catch {
        return json({ error: "JSON inválido." }, 400);
      }

      const { client_id, callback_url, code, code_verifier } = input || {};
      if (client_id !== env.GITHUB_CLIENT_ID || callback_url !== env.GITHUB_CALLBACK_URL) {
        return json({ error: "Configuração de aplicativo inválida." }, 400);
      }
      if (typeof code !== "string" || code.length < 1 || code.length > 2048 ||
          typeof code_verifier !== "string" || !/^[A-Za-z0-9._~-]{43,128}$/.test(code_verifier)) {
        return json({ error: "Dados PKCE inválidos." }, 400);
      }

      const tokenResponse = await fetch("https://github.com/login/oauth/access_token", {
        method: "POST",
        headers: {
          "accept": "application/json",
          "content-type": "application/json"
        },
        body: JSON.stringify({
          client_id: env.GITHUB_CLIENT_ID,
          client_secret: env.GITHUB_CLIENT_SECRET,
          code,
          redirect_uri: env.GITHUB_CALLBACK_URL,
          code_verifier
        })
      });

      const payload = await tokenResponse.json().catch(() => ({}));
      if (!tokenResponse.ok || payload.error || !payload.access_token) {
        return json({ error: "O GitHub recusou a troca do código. Reinicie a autorização." }, 401);
      }

      // Do not log or persist the token. It is returned only to the calling app.
      return json(payload, 200);
    }

    return json({ error: "Não encontrado." }, 404);
  }
};
