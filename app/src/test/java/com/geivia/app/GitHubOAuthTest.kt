package com.geivia.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubOAuthTest {
    @Test
    fun pkceChallengeMatchesRfc7636S256Example() {
        val verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"
        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", GitHubOAuth.codeChallenge(verifier))
    }

    @Test
    fun generatedVerifierAndStateHaveUrlSafeLengths() {
        val verifier = GitHubOAuth.randomVerifier()
        val state = GitHubOAuth.randomState()
        assertEquals(43, verifier.length)
        assertEquals(43, state.length)
        assertTrue(verifier.matches(Regex("^[A-Za-z0-9_-]+$")))
        assertTrue(state.matches(Regex("^[A-Za-z0-9_-]+$")))
    }

    @Test
    fun callbackAndExchangeMustShareHttpsOrigin() {
        assertTrue(GitHubOAuth.validEndpoints(
            "https://geivia-auth.example.workers.dev/callback",
            "https://geivia-auth.example.workers.dev/exchange"
        ))
        assertFalse(GitHubOAuth.validEndpoints(
            "http://geivia-auth.example.workers.dev/callback",
            "https://geivia-auth.example.workers.dev/exchange"
        ))
        assertFalse(GitHubOAuth.validEndpoints(
            "https://attacker.example/callback",
            "https://geivia-auth.example.workers.dev/exchange"
        ))
        assertFalse(GitHubOAuth.validEndpoints(
            "https://geivia-auth.example.workers.dev/callback",
            "https://geivia-auth.example.workers.dev/token"
        ))
    }

    @Test
    fun authorizationUrlIncludesPkceAndState() {
        val url = GitHubOAuth.authorizationUrl(
            "Iv1.example",
            "https://geivia-auth.example.workers.dev/callback",
            "state-value",
            "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"
        )
        assertTrue(url.startsWith("https://github.com/login/oauth/authorize?"))
        assertTrue(url.contains("code_challenge_method=S256"))
        assertTrue(url.contains("state=state-value"))
        assertTrue(url.contains("allow_signup=false"))
        assertTrue(url.contains("code_challenge=E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsTooShortVerifier() {
        GitHubOAuth.codeChallenge("short")
    }
}
