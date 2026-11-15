package com.geivia.app

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URI
import java.io.InputStream
import java.io.ByteArrayOutputStream
import java.net.URLEncoder
import java.security.KeyStore
import java.security.SecureRandom
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal fun readUtf8Limited(input: InputStream?, maxBytes: Int): String {
    require(maxBytes > 0)
    val stream = input ?: return ""
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    var total = 0
    while (true) {
        val count = stream.read(buffer)
        if (count < 0) break
        total += count
        require(total <= maxBytes) { "Resposta do GitHub excede o limite de segurança." }
        output.write(buffer, 0, count)
    }
    return output.toString(Charsets.UTF_8.name())
}

data class GitHubRepository(
    val id: Long,
    val fullName: String,
    val description: String?,
    val isPrivate: Boolean,
    val defaultBranch: String,
    val htmlUrl: String,
    val updatedAt: String?
)

data class GitHubCommit(val sha: String, val message: String, val author: String?, val date: String?, val htmlUrl: String)
data class GitHubWorkflowRun(val id: Long, val name: String, val status: String, val conclusion: String?, val branch: String?, val createdAt: String?, val htmlUrl: String)
data class GitHubPullRequest(val number: Int, val title: String, val state: String, val author: String?, val createdAt: String?, val htmlUrl: String)
data class GitHubBranch(val name: String, val isProtected: Boolean)
data class GitHubJob(val id: Long, val name: String, val status: String, val conclusion: String?)
data class GitHubArtifact(val id: Long, val name: String, val sizeBytes: Long, val expired: Boolean, val expiresAt: String?)

object GitHubOAuth {
    private val random = SecureRandom()

    fun randomVerifier(): String = randomBytes(32)
    fun randomState(): String = randomBytes(32)

    private fun randomBytes(size: Int): String {
        val bytes = ByteArray(size)
        random.nextBytes(bytes)
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    fun codeChallenge(verifier: String): String {
        require(verifier.length in 43..128) { "PKCE verifier inválido." }
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }

    fun validEndpoints(callbackUrl: String, exchangeUrl: String): Boolean = runCatching {
        val callback = URI(callbackUrl)
        val exchange = URI(exchangeUrl)
        callback.scheme == "https" && exchange.scheme == "https" &&
            callback.host != null && callback.host == exchange.host &&
            callback.port == exchange.port && callback.path == "/callback" &&
            exchange.path == "/exchange" && callback.rawUserInfo == null && exchange.rawUserInfo == null &&
            callback.rawQuery == null && exchange.rawQuery == null && callback.rawFragment == null && exchange.rawFragment == null
    }.getOrDefault(false)

    fun authorizationUrl(clientId: String, callbackUrl: String, state: String, verifier: String): String {
        require(clientId.isNotBlank()) { "Informe o Client ID da GitHub App." }
        require(callbackUrl.startsWith("https://")) { "A URL de callback precisa usar HTTPS." }
        val params = linkedMapOf(
            "client_id" to clientId,
            "redirect_uri" to callbackUrl,
            "state" to state,
            "code_challenge" to codeChallenge(verifier),
            "code_challenge_method" to "S256",
            "allow_signup" to "false"
        )
        return "https://github.com/login/oauth/authorize?" + params.entries.joinToString("&") {
            "${encode(it.key)}=${encode(it.value)}"
        }
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
}

class GitHubTokenStore(context: Context) {
    private val prefs = context.getSharedPreferences("geivia_github_secure", Context.MODE_PRIVATE)
    private val alias = "geivia_github_token_key"

    private fun encryptValue(value: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP)
    }

    private fun decryptValue(encoded: String): String {
        val payload = Base64.decode(encoded, Base64.NO_WRAP)
        require(payload.size > 12) { "Dado protegido local inválido." }
        val iv = payload.copyOfRange(0, 12)
        val encrypted = payload.copyOfRange(12, payload.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
        return String(cipher.doFinal(encrypted), Charsets.UTF_8)
    }

    fun save(tokenResponse: String) {
        require(JSONObject(tokenResponse).optString("access_token").isNotBlank()) {
            "Resposta de autorização sem access_token."
        }
        val storedResponse = JSONObject(tokenResponse).put("_geivia_saved_at", System.currentTimeMillis()).toString()
        prefs.edit().putString("encrypted_token_response", encryptValue(storedResponse)).apply()
    }

    fun readTokenResponse(): String? {
        val encoded = prefs.getString("encrypted_token_response", null) ?: return null
        return runCatching { decryptValue(encoded) }.getOrElse {
            clear()
            null
        }
    }

    fun savePendingAuthorization(verifier: String, state: String, startedAt: Long) {
        val pending = JSONObject()
            .put("verifier", verifier)
            .put("state", state)
            .put("startedAt", startedAt)
            .toString()
        prefs.edit().putString("encrypted_pending_authorization", encryptValue(pending)).apply()
    }

    fun readPendingAuthorization(): JSONObject? {
        val encoded = prefs.getString("encrypted_pending_authorization", null) ?: return null
        return runCatching { JSONObject(decryptValue(encoded)) }.getOrNull()
    }

    fun clearPendingAuthorization() {
        prefs.edit().remove("encrypted_pending_authorization").apply()
    }

    fun accessToken(): String? = readTokenResponse()?.let {
        runCatching { JSONObject(it).optString("access_token").takeIf(String::isNotBlank) }.getOrNull()
    }

    fun needsRefresh(bufferMillis: Long = 120_000L): Boolean {
        val raw = readTokenResponse() ?: return false
        val json = JSONObject(raw)
        val expiresIn = json.optLong("expires_in", 0L)
        if (expiresIn <= 0L) return false
        val savedAt = json.optLong("_geivia_saved_at", 0L)
        if (savedAt <= 0L) return true
        return System.currentTimeMillis() >= savedAt + expiresIn * 1000L - bufferMillis
    }

    fun refreshTokenValue(): String? = readTokenResponse()?.let {
        JSONObject(it).optString("refresh_token").takeIf { value -> value.isNotBlank() && value != "null" }
    }

    fun clear() {
        prefs.edit().remove("encrypted_token_response").remove("encrypted_pending_authorization").apply()
        runCatching {
            val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            keyStore.deleteEntry(alias)
        }
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }
}

object GitHubAuthClient {
    fun ensureFreshAccessToken(exchangeUrl: String, clientId: String, callbackUrl: String, tokenStore: GitHubTokenStore): String? {
        val current = tokenStore.accessToken() ?: return null
        if (!tokenStore.needsRefresh()) return current
        val refreshCredential = tokenStore.refreshTokenValue()
            ?: throw IllegalStateException("A autorização expirou e não há refresh token. Conecte novamente.")
        val refreshUrl = exchangeUrl.removeSuffix("/exchange") + "/refresh"
        val refreshed = refreshToken(refreshUrl, clientId, callbackUrl, refreshCredential)
        tokenStore.save(refreshed)
        return tokenStore.accessToken()
            ?: throw IllegalStateException("O GitHub não retornou um novo token válido.")
    }

    fun refreshToken(refreshUrl: String, clientId: String, callbackUrl: String, refreshToken: String): String {
        require(refreshUrl.startsWith("https://") && GitHubOAuth.validEndpoints(callbackUrl, refreshUrl.removeSuffix("/refresh") + "/exchange")) {
            "O serviço de renovação precisa usar o mesmo domínio HTTPS."
        }
        val connection = (URL(refreshUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 20_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
        }
        try {
            val body = JSONObject()
                .put("client_id", clientId)
                .put("callback_url", callbackUrl)
                .put("refresh_token", refreshToken)
                .toString()
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val response = readUtf8Limited(stream, 64 * 1024)
            if (status == 401) throw IllegalStateException("A autorização expirou ou foi revogada. Conecte novamente.")
            if (status !in 200..299) throw IllegalStateException("Falha ao renovar a autorização GitHub (HTTP $status).")
            require(JSONObject(response).optString("access_token").isNotBlank()) {
                "O serviço não retornou um novo token válido."
            }
            return response
        } finally {
            connection.disconnect()
        }
    }

    fun exchangeCode(exchangeUrl: String, clientId: String, callbackUrl: String, code: String, verifier: String): String {
        require(exchangeUrl.startsWith("https://") && GitHubOAuth.validEndpoints(callbackUrl, exchangeUrl)) { "Callback e serviço de autorização devem usar HTTPS no mesmo domínio." }
        val connection = (URL(exchangeUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 20_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
        }
        try {
            val body = JSONObject()
                .put("client_id", clientId)
                .put("callback_url", callbackUrl)
                .put("code", code)
                .put("code_verifier", verifier)
                .toString()
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val response = readUtf8Limited(stream, 64 * 1024)
            if (status !in 200..299) {
                throw IllegalStateException("Falha na autorização GitHub (HTTP $status).")
            }
            require(JSONObject(response).optString("access_token").isNotBlank()) {
                "O serviço não retornou um token válido."
            }
            return response
        } finally {
            connection.disconnect()
        }
    }
}

object GitHubApiClient {
    private fun repositoryPath(fullName: String): String {
        require(fullName.matches(Regex("^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$"))) { "Identificador de repositório inválido." }
        return fullName
    }

    private fun getJson(accessToken: String, path: String): String {
        val connection = (URL("https://api.github.com$path").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        }
        try {
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val response = readUtf8Limited(stream, 2 * 1024 * 1024)
            if (status == 401) throw IllegalStateException("A autorização expirou ou foi revogada. Conecte novamente.")
            if (status == 403) throw IllegalStateException("O GitHub negou o acesso ou aplicou um limite. Confira as permissões da GitHub App.")
            if (status !in 200..299) throw IllegalStateException("Não foi possível consultar o GitHub (HTTP $status).")
            return response
        } finally {
            connection.disconnect()
        }
    }

    fun authenticatedLogin(accessToken: String): String =
        JSONObject(getJson(accessToken, "/user")).optString("login").takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("O GitHub não retornou o usuário autenticado.")

    fun listRepositories(accessToken: String): List<GitHubRepository> {
        val result = mutableListOf<GitHubRepository>()
        for (page in 1..10) {
            val json = JSONArray(getJson(accessToken, "/user/repos?visibility=all&affiliation=owner,collaborator,organization_member&sort=updated&per_page=100&page=$page"))
            val current = (0 until json.length()).mapNotNull { index ->
                val item = json.optJSONObject(index) ?: return@mapNotNull null
                val name = item.optString("full_name")
                if (name.isBlank()) return@mapNotNull null
                GitHubRepository(
                    id = item.optLong("id"),
                    fullName = name,
                    description = item.optString("description").takeIf { it != "null" && it.isNotBlank() },
                    isPrivate = item.optBoolean("private"),
                    defaultBranch = item.optString("default_branch"),
                    htmlUrl = item.optString("html_url"),
                    updatedAt = item.optString("updated_at").takeIf { it != "null" && it.isNotBlank() }
                )
            }
            result += current
            if (json.length() < 100) break
        }
        return result
    }

    fun listBranches(accessToken: String, repository: String): List<GitHubBranch> {
        val json = JSONArray(getJson(accessToken, "/repos/${repositoryPath(repository)}/branches?per_page=100"))
        return (0 until json.length()).mapNotNull { index ->
            val item = json.optJSONObject(index) ?: return@mapNotNull null
            item.optString("name").takeIf { it.isNotBlank() }?.let { GitHubBranch(it, item.optBoolean("protected")) }
        }
    }

    fun listJobs(accessToken: String, repository: String, runId: Long): List<GitHubJob> {
        val jobs = JSONObject(getJson(accessToken, "/repos/${repositoryPath(repository)}/actions/runs/$runId/jobs?per_page=100"))
            .optJSONArray("jobs") ?: JSONArray()
        return (0 until jobs.length()).mapNotNull { index ->
            val item = jobs.optJSONObject(index) ?: return@mapNotNull null
            GitHubJob(
                id = item.optLong("id"),
                name = item.optString("name"),
                status = item.optString("status"),
                conclusion = item.optString("conclusion").takeIf { it.isNotBlank() && it != "null" }
            )
        }
    }

    fun listArtifacts(accessToken: String, repository: String, runId: Long): List<GitHubArtifact> {
        val artifacts = JSONObject(getJson(accessToken, "/repos/${repositoryPath(repository)}/actions/runs/$runId/artifacts?per_page=100"))
            .optJSONArray("artifacts") ?: JSONArray()
        return (0 until artifacts.length()).mapNotNull { index ->
            val item = artifacts.optJSONObject(index) ?: return@mapNotNull null
            GitHubArtifact(
                id = item.optLong("id"),
                name = item.optString("name"),
                sizeBytes = item.optLong("size_in_bytes"),
                expired = item.optBoolean("expired"),
                expiresAt = item.optString("expires_at").takeIf { it.isNotBlank() && it != "null" }
            )
        }
    }

    fun listCommits(accessToken: String, repository: String): List<GitHubCommit> {
        val json = JSONArray(getJson(accessToken, "/repos/${repositoryPath(repository)}/commits?per_page=20"))
        return (0 until json.length()).mapNotNull { index ->
            val item = json.optJSONObject(index) ?: return@mapNotNull null
            val commit = item.optJSONObject("commit") ?: return@mapNotNull null
            GitHubCommit(
                sha = item.optString("sha"),
                message = commit.optString("message").lineSequence().firstOrNull().orEmpty(),
                author = commit.optJSONObject("author")?.optString("name")?.takeIf { it.isNotBlank() && it != "null" },
                date = commit.optJSONObject("author")?.optString("date")?.takeIf { it.isNotBlank() && it != "null" },
                htmlUrl = item.optString("html_url")
            )
        }
    }

    fun listWorkflowRuns(accessToken: String, repository: String): List<GitHubWorkflowRun> {
        val runs = JSONObject(getJson(accessToken, "/repos/${repositoryPath(repository)}/actions/runs?per_page=15"))
            .optJSONArray("workflow_runs") ?: JSONArray()
        return (0 until runs.length()).mapNotNull { index ->
            val item = runs.optJSONObject(index) ?: return@mapNotNull null
            GitHubWorkflowRun(
                id = item.optLong("id"),
                name = item.optString("name", item.optString("display_title")),
                status = item.optString("status"),
                conclusion = item.optString("conclusion").takeIf { it.isNotBlank() && it != "null" },
                branch = item.optString("head_branch").takeIf { it.isNotBlank() && it != "null" },
                createdAt = item.optString("created_at").takeIf { it.isNotBlank() && it != "null" },
                htmlUrl = item.optString("html_url")
            )
        }
    }

    fun listPullRequests(accessToken: String, repository: String): List<GitHubPullRequest> {
        val json = JSONArray(getJson(accessToken, "/repos/${repositoryPath(repository)}/pulls?state=open&per_page=20"))
        return (0 until json.length()).mapNotNull { index ->
            val item = json.optJSONObject(index) ?: return@mapNotNull null
            GitHubPullRequest(
                number = item.optInt("number"),
                title = item.optString("title"),
                state = item.optString("state"),
                author = item.optJSONObject("user")?.optString("login")?.takeIf { it.isNotBlank() && it != "null" },
                createdAt = item.optString("created_at").takeIf { it.isNotBlank() && it != "null" },
                htmlUrl = item.optString("html_url")
            )
        }
    }
}
