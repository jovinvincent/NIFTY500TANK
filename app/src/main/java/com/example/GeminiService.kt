package com.example

import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val role: String, // "user" or "model"
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val sources: List<GroundingSource> = emptyList(),
    val isAudioTranscribed: Boolean = false,
    val modelUsed: String? = null
)

data class GroundingSource(
    val title: String,
    val url: String
)

enum class GeminiModel(val modelId: String, val displayName: String, val tagLine: String) {
    FLASH("gemini-3.5-flash", "Gemini 3.5 Flash", "Balanced & Fast"),
    PRO("gemini-3.1-pro-preview", "Gemini 3.1 Pro", "Deep Quantitative Strategy"),
    LITE("gemini-3.1-flash-lite", "Gemini 3.1 Flash-Lite", "Ultra-Fast Answers")
}

object GeminiService {
    private const val TAG = "GeminiService"
    private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private const val SYSTEM_INSTRUCTION = """
You are an expert Quantitative Mutual Fund & SIP Portfolio Strategist.
You analyze mutual funds (including large cap, mid cap, small cap, flexi cap, dynamic asset allocation, arbitrage, and hybrid funds).
Key areas of expertise:
1. XIRR compounding calculation, CAGR vs XIRR nuances, and sequence of returns risk.
2. Quantitative risk-adjusted metrics: Portfolio Alpha, Beta, Standard Deviation, Sharpe Ratio, Sortino Ratio, and Maximum Drawdown.
3. Portfolio asset allocation, factor-based risk analysis, and systematic rebalancing.
4. Smart SIP strategies (e.g. buying the dip, volatility adjustments, STP vs lump sum).
5. Comparing portfolio performance against relevant market benchmarks (Nifty 50 TRI, Nifty Midcap 150 TRI, Nifty Smallcap 250 TRI, Nifty 500 TRI).
Provide clear, actionable, mathematically sound, and objective financial insights. Format your responses with clear headings, bullet points, and highlight key numbers where appropriate.
"""

    private fun getApiKey(): String {
        return try {
            val key = BuildConfig.GEMINI_API_KEY
            if (key.isNullOrBlank() || key == "MY_GEMINI_API_KEY") "" else key
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * Multi-turn chat message sending with optional Search Grounding
     */
    suspend fun sendChatMessage(
        history: List<ChatMessage>,
        userMessage: String,
        model: GeminiModel = GeminiModel.FLASH,
        useSearchGrounding: Boolean = false
    ): Result<ChatMessage> = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        if (apiKey.isEmpty()) {
            return@withContext Result.failure(
                IllegalStateException("Gemini API key is not configured. Please set GEMINI_API_KEY in the AI Studio Secrets panel or .env file.")
            )
        }

        // When search grounding is enabled, use gemini-3.5-flash
        val selectedModelId = if (useSearchGrounding) "gemini-3.5-flash" else model.modelId
        val endpoint = "$BASE_URL/$selectedModelId:generateContent?key=$apiKey"

        try {
            val requestJson = JSONObject()

            // System Instruction
            val sysInstructionObj = JSONObject()
            val sysParts = JSONArray()
            sysParts.put(JSONObject().put("text", SYSTEM_INSTRUCTION))
            sysInstructionObj.put("parts", sysParts)
            requestJson.put("systemInstruction", sysInstructionObj)

            // Contents (multi-turn history)
            val contentsArray = JSONArray()
            for (msg in history.takeLast(10)) {
                val msgObj = JSONObject()
                msgObj.put("role", if (msg.role == "user") "user" else "model")
                val parts = JSONArray()
                parts.put(JSONObject().put("text", msg.content))
                msgObj.put("parts", parts)
                contentsArray.put(msgObj)
            }

            // Current user message
            val currentMsgObj = JSONObject()
            currentMsgObj.put("role", "user")
            val currentParts = JSONArray()
            currentParts.put(JSONObject().put("text", userMessage))
            currentMsgObj.put("parts", currentParts)
            contentsArray.put(currentMsgObj)

            requestJson.put("contents", contentsArray)

            // Search Grounding Tool
            if (useSearchGrounding) {
                val toolsArray = JSONArray()
                val toolObj = JSONObject()
                toolObj.put("googleSearch", JSONObject())
                toolsArray.put(toolObj)
                requestJson.put("tools", toolsArray)
            }

            val requestBody = requestJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url(endpoint)
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                Log.e(TAG, "Gemini call failed with code ${response.code}: $responseBody")
                val errorMsg = try {
                    val errJson = JSONObject(responseBody)
                    errJson.optJSONObject("error")?.optString("message") ?: "API Error ${response.code}"
                } catch (e: Exception) {
                    "API Error ${response.code}: $responseBody"
                }
                return@withContext Result.failure(Exception(errorMsg))
            }

            val rootJson = JSONObject(responseBody)
            val candidates = rootJson.optJSONArray("candidates")
            if (candidates == null || candidates.length() == 0) {
                return@withContext Result.failure(Exception("No response received from Gemini."))
            }

            val candidate = candidates.getJSONObject(0)
            val contentObj = candidate.optJSONObject("content")
            val parts = contentObj?.optJSONArray("parts")

            val replyTextBuilder = StringBuilder()
            if (parts != null) {
                for (i in 0 until parts.length()) {
                    val p = parts.getJSONObject(i)
                    if (p.has("text")) {
                        replyTextBuilder.append(p.getString("text"))
                    }
                }
            }

            // Extract grounding search metadata if present
            val sources = mutableListOf<GroundingSource>()
            val groundingMetadata = candidate.optJSONObject("groundingMetadata")
            if (groundingMetadata != null) {
                val searchChunks = groundingMetadata.optJSONArray("groundingChunks")
                if (searchChunks != null) {
                    for (i in 0 until searchChunks.length()) {
                        val chunk = searchChunks.optJSONObject(i)
                        val web = chunk?.optJSONObject("web")
                        if (web != null) {
                            val uri = web.optString("uri")
                            val title = web.optString("title", uri)
                            if (uri.isNotBlank()) {
                                sources.add(GroundingSource(title = title, url = uri))
                            }
                        }
                    }
                }
            }

            val reply = replyTextBuilder.toString().trim()
            Result.success(
                ChatMessage(
                    role = "model",
                    content = if (reply.isNotEmpty()) reply else "Unable to formulate a response.",
                    sources = sources,
                    modelUsed = selectedModelId
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error executing Gemini request", e)
            Result.failure(e)
        }
    }

    /**
     * Audio Transcription using gemini-3.5-transcribe
     */
    suspend fun transcribeAudio(
        audioBytes: ByteArray,
        mimeType: String = "audio/mp4"
    ): Result<String> = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        if (apiKey.isEmpty()) {
            return@withContext Result.failure(
                IllegalStateException("Gemini API key is not configured. Please set GEMINI_API_KEY in secrets.")
            )
        }

        val endpoint = "$BASE_URL/gemini-3.5-transcribe:generateContent?key=$apiKey"

        try {
            val base64Data = Base64.encodeToString(audioBytes, Base64.NO_WRAP)

            val requestJson = JSONObject()
            val contentsArray = JSONArray()
            val contentObj = JSONObject()
            val partsArray = JSONArray()

            // Inline audio data part
            val audioPart = JSONObject()
            val inlineData = JSONObject()
            inlineData.put("mimeType", mimeType)
            inlineData.put("data", base64Data)
            audioPart.put("inlineData", inlineData)
            partsArray.put(audioPart)

            // Instruction prompt part
            val promptPart = JSONObject()
            promptPart.put("text", "Please accurately transcribe the following voice audio prompt verbatim.")
            partsArray.put(promptPart)

            contentObj.put("parts", partsArray)
            contentsArray.put(contentObj)
            requestJson.put("contents", contentsArray)

            val requestBody = requestJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url(endpoint)
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                Log.e(TAG, "Transcribe failed with code ${response.code}: $responseBody")
                return@withContext Result.failure(Exception("Transcription error (${response.code}): $responseBody"))
            }

            val rootJson = JSONObject(responseBody)
            val candidates = rootJson.optJSONArray("candidates")
            if (candidates != null && candidates.length() > 0) {
                val candidate = candidates.getJSONObject(0)
                val content = candidate.optJSONObject("content")
                val parts = content?.optJSONArray("parts")
                val textBuilder = StringBuilder()
                if (parts != null) {
                    for (i in 0 until parts.length()) {
                        val p = parts.getJSONObject(i)
                        if (p.has("text")) {
                            textBuilder.append(p.getString("text"))
                        }
                    }
                }
                val transcribed = textBuilder.toString().trim()
                if (transcribed.isNotEmpty()) {
                    return@withContext Result.success(transcribed)
                }
            }

            Result.failure(Exception("Empty transcription result received."))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to transcribe audio", e)
            Result.failure(e)
        }
    }
}
