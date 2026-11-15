package com.geivia.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class GoogleProject(
    val projectId: String,
    val displayName: String,
    val projectNumber: String
)

object GoogleApiRepository {
    suspend fun getUserProfile(accessToken: String): JSONObject =
        getJson("https://openidconnect.googleapis.com/v1/userinfo", accessToken)

    suspend fun listFirebaseProjects(accessToken: String): List<GoogleProject> {
        val output = mutableListOf<GoogleProject>()
        var pageToken: String? = null
        do {
            val suffix = pageToken?.let { "&pageToken=" + encode(it) }.orEmpty()
            val json = getJson("https://firebase.googleapis.com/v1beta1/projects?pageSize=100$suffix", accessToken)
            val results = json.optJSONArray("results")
            if (results != null) {
                for (i in 0 until results.length()) {
                    val item = results.optJSONObject(i) ?: continue
                    val id = item.optString("projectId")
                    if (id.isNotBlank()) output.add(
                        GoogleProject(id, item.optString("displayName", id), item.optString("projectNumber"))
                    )
                }
            }
            pageToken = json.optString("nextPageToken").takeIf { it.isNotBlank() }
        } while (pageToken != null)
        return output
    }

    suspend fun listCloudProjects(accessToken: String): List<GoogleProject> {
        val output = mutableListOf<GoogleProject>()
        var pageToken: String? = null
        do {
            val suffix = pageToken?.let { "&pageToken=" + encode(it) }.orEmpty()
            val json = getJson("https://cloudresourcemanager.googleapis.com/v1beta1/projects?pageSize=100$suffix", accessToken)
            val results = json.optJSONArray("projects")
            if (results != null) {
                for (i in 0 until results.length()) {
                    val item = results.optJSONObject(i) ?: continue
                    if (item.optString("lifecycleState") == "DELETE_REQUESTED") continue
                    val id = item.optString("projectId")
                    if (id.isNotBlank()) output.add(
                        GoogleProject(id, item.optString("name", id), item.optString("projectNumber"))
                    )
                }
            }
            pageToken = json.optString("nextPageToken").takeIf { it.isNotBlank() }
        } while (pageToken != null)
        return output
    }

    suspend fun listEnabledServices(accessToken: String, projectNumber: String): List<String> {
        require(projectNumber.isNotBlank()) { "O projeto selecionado não possui número disponível para a consulta." }
        val parent = encode("projects/$projectNumber")
        val output = mutableListOf<String>()
        var pageToken: String? = null
        do {
            val suffix = pageToken?.let { "&pageToken=" + encode(it) }.orEmpty()
            val json = getJson(
                "https://serviceusage.googleapis.com/v1/$parent/services?filter=state%3AENABLED&pageSize=200$suffix",
                accessToken
            )
            val services = json.optJSONArray("services")
            if (services != null) {
                for (index in 0 until services.length()) {
                    val resourceName = services.optJSONObject(index)?.optString("name").orEmpty()
                    if (resourceName.isNotBlank()) output.add(resourceName.substringAfterLast("/"))
                }
            }
            pageToken = json.optString("nextPageToken").takeIf { it.isNotBlank() }
        } while (pageToken != null)
        return output
    }

    suspend fun getBillingInfo(accessToken: String, projectId: String): String {
        val json = getJson("https://cloudbilling.googleapis.com/v1/projects/${encode(projectId)}/billingInfo", accessToken)
        if (!json.has("billingEnabled")) throw IOException("O Google não retornou o estado de faturamento. A conta pode não ter permissão para consultar este projeto.")
        return if (json.optBoolean("billingEnabled")) {
            "Há uma conta de faturamento associada a este projeto."
        } else {
            "A consulta confirmou que não há faturamento associado a este projeto."
        }
    }

    private suspend fun getJson(url: String, accessToken: String): JSONObject = withContext(Dispatchers.IO) {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15000
            readTimeout = 20000
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("Accept", "application/json")
        }
        try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val message = runCatching { JSONObject(body).optString("message") }.getOrNull().orEmpty()
                throw IOException("Google API HTTP $code" + if (message.isNotBlank()) ": $message" else "")
            }
            JSONObject(body)
        } finally {
            connection.disconnect()
        }
    }

    private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())
}
