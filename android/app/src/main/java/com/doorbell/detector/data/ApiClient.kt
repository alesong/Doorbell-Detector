package com.doorbell.detector.data

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class ApiClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    fun sendNotification(
        baseUrl: String,
        appName: String,
        packageName: String,
        title: String,
        body: String
    ): Result<String> {
        return try {
            val json = JSONObject().apply {
                put("app_name", appName)
                put("package_name", packageName)
                put("title", title)
                put("body", body)
            }

            val request = Request.Builder()
                .url("$baseUrl/api/notifications")
                .post(json.toString().toRequestBody(jsonMediaType))
                .build()

            val response = client.newCall(request).execute()

            if (response.isSuccessful) {
                Result.success(response.body?.string() ?: "")
            } else {
                Result.failure(Exception("Error ${response.code}: ${response.message}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun testConnection(baseUrl: String): Result<String> {
        return try {
            val request = Request.Builder()
                .url("$baseUrl/health")
                .get()
                .build()

            val response = client.newCall(request).execute()

            if (response.isSuccessful) {
                val body = response.body?.string() ?: ""
                Result.success(body)
            } else {
                Result.failure(Exception("Error ${response.code}: ${response.message}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
