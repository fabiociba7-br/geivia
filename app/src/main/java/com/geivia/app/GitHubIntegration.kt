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
import java.net.URLEncoder
import java.security.KeyStore
import java.security.SecureRandom
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class GitHubRepository(
    val id: Long,
    val fullName: String,
    val description: String?,
    val isPrivate: Boolean,
    val defaultBranch: String,
    val htmlUrl: String,
    val updatedAt: String?
)

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

    fun save(tokenResponse: String) {
        require(JSONObject(tokenResponse).optString("access_token").isNotBlank()) {
            "Resposta de autorização sem access_token."
        }
        val key = getOrCreateKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val encrypted = cipher.doFinal(tokenResponse.toByteArray(Charsets.UTF_8))
        val payload = cipher.iv + encrypted
        prefs.edit().putString("encrypted_token_response", Base64.encodeToString(payload, Base64.NO_WRAP)).apply()
    }

    fun readTokenResponse(): String? {
        val encoded = prefs.getString("encrypted_token_response", null) ?: return null
        return runCatching {
            val payload = Base64.decode(encoded, Base64.NO_WRAP)
            require(payload.size > 12) { "Token local inválido." }
            val iv = payload.copyOfRange(0, 12)
            val encrypted = payload.copyOfRange(12, payload.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(encrypted), Charsets.UTF_8)
        }.getOrElse {
            clear()
            null
        }
    }

    fun accessToken(): String? = readTokenResponse()?.let {
        runCatching { JSONObject(it).optString("access_token").takeIf(String::isNotBlank) }.getOrNull()
    }

    fun clear() {
        prefs.edit().remove("encrypted_token_response").apply()
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
            val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
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
    fun listRepositories(accessToken: String): List<GitHubRepository> {
        val connection = (URL("https://api.github.com/user/repos?visibility=all&affiliation=owner,collaborator,organization_member&sort=updated&per_page=100").openConnection() as HttpURLConnection).apply {
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
            val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (status == 401) throw IllegalStateException("A autorização expirou ou foi revogada. Conecte novamente.")
            if (status == 403) throw IllegalStateException("O GitHub negou o acesso ou aplicou um limite. Confira as permissões da GitHub App.")
            if (status !in 200..299) throw IllegalStateException("Não foi possível listar repositórios (HTTP $status).")
            val json = JSONArray(response)
            return (0 until json.length()).mapNotNull { index ->
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
        } finally {
            connection.disconnect()
        }
    }
}
