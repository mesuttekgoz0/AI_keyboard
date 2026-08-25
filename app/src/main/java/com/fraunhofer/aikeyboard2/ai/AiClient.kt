package com.fraunhofer.aikeyboard2.ai

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

/**
 * NVIDIA NIM (integrate.api.nvidia.com) üzerinden chat-completions isteği atan istemci.
 * Retrofit/OkHttp gibi ek bağımlılık gerektirmez — HttpURLConnection + org.json yeterli.
 */
object AiClient {

    private const val ENDPOINT = "https://integrate.api.nvidia.com/v1/chat/completions"
    private const val MODEL = "meta/llama-3.3-70b-instruct"
    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 30_000

    private const val MIN_MAX_TOKENS = 200
    private const val MAX_MAX_TOKENS = 800
    private const val FREE_GENERATION_MAX_TOKENS = 400
    private const val CHARS_PER_TOKEN_ESTIMATE = 3

    sealed class AiResult {
        data class Success(val text: String) : AiResult()
        data class Failure(val message: String) : AiResult()
    }

    /**
     * Senkron çağrı — çağıran taraf arka plan thread'inde çalıştırmalı.
     * [selectedText] null/boşsa serbest üretim, doluysa seçili metni [instruction]'a göre düzenler.
     */
    fun requestCompletion(apiKey: String, instruction: String, selectedText: String?): AiResult {
        if (apiKey.isBlank()) return AiResult.Failure("API key girilmedi")

        val systemPrompt: String
        val userPrompt: String
        if (!selectedText.isNullOrBlank()) {
            systemPrompt = "Kullanıcının seçtiği metni, verdiği talimata göre düzenle. " +
                "SADECE düzenlenmiş metni döndür; açıklama, tırnak işareti veya markdown ekleme."
            userPrompt = "Talimat: $instruction\n\nMetin:\n$selectedText"
        } else {
            systemPrompt = "Kullanıcının istediğini üret. " +
                "SADECE sonuç metnini döndür; açıklama, tırnak işareti veya markdown ekleme."
            userPrompt = instruction
        }

        val requestBody = JSONObject().apply {
            put("model", MODEL)
            put("messages", JSONArray().apply {
                put(JSONObject().apply { put("role", "system"); put("content", systemPrompt) })
                put(JSONObject().apply { put("role", "user"); put("content", userPrompt) })
            })
            put("temperature", 0.5)
            put("top_p", 1)
            put("max_tokens", estimateMaxTokens(selectedText))
            put("stream", false)
        }

        var connection: HttpURLConnection? = null
        try {
            connection = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                doOutput = true
                setRequestProperty("Authorization", "Bearer $apiKey")
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
            }

            OutputStreamWriter(connection.outputStream, StandardCharsets.UTF_8).use { writer ->
                writer.write(requestBody.toString())
            }

            val statusCode = connection.responseCode
            val stream = if (statusCode in 200..299) connection.inputStream else connection.errorStream
            val responseBody = BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8)).use { it.readText() }

            if (statusCode !in 200..299) {
                return AiResult.Failure(friendlyErrorMessage(statusCode, responseBody))
            }

            val content = JSONObject(responseBody)
                .getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
                .trim()
                .trim('"', '“', '”')

            return if (content.isNotBlank()) AiResult.Success(content)
                   else AiResult.Failure("AI boş yanıt döndürdü")
        } catch (e: IOException) {
            return AiResult.Failure("Bağlantı hatası: ${e.message ?: "internet yok"}")
        } catch (e: Exception) {
            return AiResult.Failure("Beklenmeyen hata: ${e.message}")
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * Metin düzenleme görevinde (seçili metin var) sonucun girdiden çok uzun olması beklenmez —
     * bu yüzden token sınırını girdi uzunluğuna göre belirler (sabit yüksek limitin gereksiz
     * beklemeye yol açmasını önler). Serbest üretimde (seçim yok) sabit orta bir limit kullanılır.
     */
    private fun estimateMaxTokens(selectedText: String?): Int {
        if (selectedText.isNullOrBlank()) return FREE_GENERATION_MAX_TOKENS
        val estimatedInputTokens = selectedText.length / CHARS_PER_TOKEN_ESTIMATE
        return (estimatedInputTokens * 2).coerceIn(MIN_MAX_TOKENS, MAX_MAX_TOKENS)
    }

    private fun friendlyErrorMessage(statusCode: Int, responseBody: String): String = when (statusCode) {
        401, 403 -> "API key geçersiz veya reddedildi"
        429 -> "İstek limiti aşıldı, biraz sonra tekrar dene"
        in 500..599 -> "NVIDIA NIM sunucu hatası, tekrar dene"
        else -> "İstek başarısız (kod $statusCode): ${responseBody.take(200)}"
    }
}
