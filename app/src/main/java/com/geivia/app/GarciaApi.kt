package com.geivia.app

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import java.util.concurrent.TimeUnit

internal class GarciaTokenVault(context: Context) {
    private val prefs = context.getSharedPreferences("geivia_garcia_secure", Context.MODE_PRIVATE)
    private val alias = "geivia.garcia.device-token.v1"

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
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

    fun read(): String? = runCatching {
        val packed = prefs.getString("device_token", null) ?: return null
        val bytes = Base64.decode(packed, Base64.NO_WRAP)
        require(bytes.size > 12)
        val iv = bytes.copyOfRange(0, 12)
        val encrypted = bytes.copyOfRange(12, bytes.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        String(cipher.doFinal(encrypted), Charsets.UTF_8)
    }.getOrNull()

    fun save(token: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val packed = cipher.iv + cipher.doFinal(token.toByteArray(Charsets.UTF_8))
        prefs.edit().putString("device_token", Base64.encodeToString(packed, Base64.NO_WRAP)).apply()
    }

    fun clear() {
        prefs.edit().remove("device_token").apply()
    }
}

internal class GarciaApi(private val vault: GarciaTokenVault) {
    companion object {
        const val BASE_URL = "https://ciba-ai.fabiociba7.workers.dev"
        const val APP_ID = "com.geivia.app"
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private fun request(path: String, payload: JSONObject? = null, authenticated: Boolean = false): JSONObject {
        val builder = Request.Builder().url(BASE_URL + path).header("Accept", "application/json")
        if (authenticated) {
            val token = vault.read() ?: error("GEIVIA ainda não está pareada com a Garcia.")
            builder.header("Authorization", "Bearer $token")
        }
        if (payload == null) builder.get()
        else builder.post(payload.toString().toRequestBody(JSON))
        http.newCall(builder.build()).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            val result = runCatching { JSONObject(raw) }
                .getOrElse { JSONObject().put("error", "Resposta inválida do Ciba AI") }
            if (!response.isSuccessful) {
                val code = result.optString("error", "Falha HTTP ${response.code}")
                val diagnostic = result.optString("diagnostic_id")
                error("HTTP ${response.code}: $code" + if (diagnostic.isNotBlank()) " · diagnóstico $diagnostic" else "")
            }
            return result
        }
    }

    fun status(): JSONObject = request("/v1/status")

    fun activate(registrationCode: String, pairingCode: String) {
        val registered = request(
            "/v1/auth/register",
            JSONObject().put("app_id", APP_ID).put("invitation_code", registrationCode)
        )
        val token = registered.optString("access_token")
        require(token.isNotBlank()) { "O Ciba AI não retornou a credencial do dispositivo." }
        vault.save(token)
        try {
            request(
                "/v1/owner/pair",
                JSONObject().put("pairing_invitation_code", pairingCode),
                authenticated = true
            )
        } catch (error: Exception) {
            vault.clear()
            throw error
        }
    }

    fun chat(messages: JSONArray): String {
        val result = request(
            "/v1/ai/chat",
            JSONObject().put("messages", messages),
            authenticated = true
        )
        return result.optString("response").ifBlank { "A Garcia retornou uma resposta vazia." }
    }
}
