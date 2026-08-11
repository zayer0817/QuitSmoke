package com.quitsmoke.app.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * DeepSeek AI API 客户端
 * 兼容 OpenAI API 格式，用于生成月度吸烟数据分析报告
 */
class DeepSeekClient(private val apiKey: String) {

    companion object {
        private const val BASE_URL = "https://api.deepseek.com/v1/chat/completions"
        private const val MODEL = "deepseek-chat"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * 发送 prompt 给 DeepSeek，返回 AI 生成的文本
     */
    suspend fun chat(prompt: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val messages = JSONArray()
            messages.put(JSONObject().put("role", "user").put("content", prompt))

            val requestBody = JSONObject()
                .put("model", MODEL)
                .put("messages", messages)
                .put("stream", false)
                .put("max_tokens", 2000)
                .put("temperature", 0.7)
                .toString()

            val request = Request.Builder()
                .url(BASE_URL)
                .header("Authorization", "Bearer $apiKey")
                .header("Content-Type", "application/json")
                .post(requestBody.toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    Exception("API 错误 ${response.code}: ${responseBody?.take(200)}")
                )
            }

            val json = JSONObject(responseBody ?: "{}")
            val content = json
                .optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("message")
                ?.optString("content")
                ?: ""

            if (content.isBlank()) {
                Result.failure(Exception("AI 返回内容为空"))
            } else {
                Result.success(content)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
