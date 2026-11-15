import test from "node:test";
import assert from "node:assert/strict";
import worker from "./index.js";

const env = {
  GITHUB_CLIENT_ID: "Iv1.test-client",
  GITHUB_CLIENT_SECRET: "test-secret-not-real",
  GITHUB_CALLBACK_URL: "https://geivia-auth.example.workers.dev/callback"
};

test("health endpoint reports service status without secrets", async () => {
  const response = await worker.fetch(new Request("https://worker.example/health"), env);
  assert.equal(response.status, 200);
  assert.deepEqual(await response.json(), { service: "GEIVIA GitHub OAuth", status: "ok" });
});

test("callback redirects only code and state to Android deep link", async () => {
  const response = await worker.fetch(
    new Request("https://worker.example/callback?code=one-time-code&state=random-state"),
    env
  );
  assert.equal(response.status, 302);
  const location = response.headers.get("location");
  assert.ok(location.startsWith("geivia://github/callback?"));
  assert.match(location, /code=one-time-code/);
  assert.match(location, /state=random-state/);
  assert.doesNotMatch(location, /access_token|client_secret/i);
  assert.equal(response.headers.get("cache-control"), "no-store");
});

test("callback rejects missing code or state", async () => {
  const response = await worker.fetch(
    new Request("https://worker.example/callback?code=one-time-code"),
    env
  );
  assert.equal(response.status, 400);
});

test("exchange rejects mismatched client configuration before contacting GitHub", async () => {
  const originalFetch = globalThis.fetch;
  let called = false;
  globalThis.fetch = async () => {
    called = true;
    return new Response("{}", { status: 200 });
  };
  try {
    const response = await worker.fetch(new Request("https://worker.example/exchange", {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({
        client_id: "wrong-client",
        callback_url: env.GITHUB_CALLBACK_URL,
        code: "code",
        code_verifier: "A".repeat(43)
      })
    }), env);
    assert.equal(response.status, 400);
    assert.equal(called, false);
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("exchange rejects declared oversized request before parsing", async () => {
  const response = await worker.fetch(new Request("https://worker.example/exchange", {
    method: "POST",
    headers: { "content-type": "application/json", "content-length": "16385" },
    body: "{}"
  }), env);
  assert.equal(response.status, 413);
});

test("exchange validates PKCE verifier before contacting GitHub", async () => {
  const originalFetch = globalThis.fetch;
  let called = false;
  globalThis.fetch = async () => {
    called = true;
    return new Response("{}", { status: 200 });
  };
  try {
    const response = await worker.fetch(new Request("https://worker.example/exchange", {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({
        client_id: env.GITHUB_CLIENT_ID,
        callback_url: env.GITHUB_CALLBACK_URL,
        code: "code",
        code_verifier: "short"
      })
    }), env);
    assert.equal(response.status, 400);
    assert.equal(called, false);
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("exchange returns GitHub token response and never logs or stores it", async () => {
  const originalFetch = globalThis.fetch;
  let sentBody;
  globalThis.fetch = async (url, init) => {
    assert.equal(url, "https://github.com/login/oauth/access_token");
    sentBody = JSON.parse(init.body);
    return new Response(JSON.stringify({
      access_token: "ghu_test_access_token",
      token_type: "bearer"
    }), { status: 200, headers: { "content-type": "application/json" } });
  };
  try {
    const response = await worker.fetch(new Request("https://worker.example/exchange", {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({
        client_id: env.GITHUB_CLIENT_ID,
        callback_url: env.GITHUB_CALLBACK_URL,
        code: "one-time-code",
        code_verifier: "A".repeat(43)
      })
    }), env);
    assert.equal(response.status, 200);
    assert.equal((await response.json()).access_token, "ghu_test_access_token");
    assert.equal(sentBody.client_secret, env.GITHUB_CLIENT_SECRET);
    assert.equal(sentBody.code_verifier, "A".repeat(43));
    assert.equal(sentBody.redirect_uri, env.GITHUB_CALLBACK_URL);
  } finally {
    globalThis.fetch = originalFetch;
  }
});
