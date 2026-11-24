package com.example.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class WebhookResult(
    val success: Boolean,
    val statusCode: Int,
    val message: String,
    val latencyMs: Long
)

object WebhookClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .writeTimeout(12, TimeUnit.SECONDS)
        .build()

    suspend fun pingUrl(targetUrl: String): WebhookResult = withContext(Dispatchers.IO) {
        val normalized = targetUrl.trim()
        if (!normalized.startsWith("https://") && !normalized.startsWith("http://")) {
            return@withContext WebhookResult(false, 0, "URL must start with https:// or http://", 0L)
        }
        val start = System.currentTimeMillis()
        try {
            val request = Request.Builder().url(normalized).get().build()
            client.newCall(request).execute().use { response ->
                val elapsed = System.currentTimeMillis() - start
                WebhookResult(
                    success = response.isSuccessful,
                    statusCode = response.code,
                    message = "HTTP ${response.code} ${response.message}",
                    latencyMs = elapsed
                )
            }
        } catch (e: Exception) {
            val elapsed = System.currentTimeMillis() - start
            WebhookResult(false, 0, e.localizedMessage ?: "Network error", elapsed)
        }
    }

    suspend fun dispatchJsonWebhook(webhookUrl: String, payload: JSONObject): WebhookResult =
        withContext(Dispatchers.IO) {
            val normalized = webhookUrl.trim()
            if (!normalized.startsWith("https://") && !normalized.startsWith("http://")) {
                return@withContext WebhookResult(false, 0, "Webhook URL must start with https://", 0L)
            }
            val start = System.currentTimeMillis()
            try {
                val body = payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
                val request = Request.Builder()
                    .url(normalized)
                    .post(body)
                    .addHeader("X-Ghazalo-Event", "restaurant.sync")
                    .build()
                client.newCall(request).execute().use { response ->
                    val elapsed = System.currentTimeMillis() - start
                    WebhookResult(
                        success = response.isSuccessful,
                        statusCode = response.code,
                        message = "HTTP ${response.code} ${response.message}",
                        latencyMs = elapsed
                    )
                }
            } catch (e: Exception) {
                val elapsed = System.currentTimeMillis() - start
                WebhookResult(false, 0, e.localizedMessage ?: "Webhook delivery failed", elapsed)
            }
        }
}
