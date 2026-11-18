package com.aigame.speech

import kotlinx.coroutines.future.await
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

// ============================================================
//  לקוח קטן ל-Gemini API (המפתח מ-Google AI Studio). משרת גם את
//  ההקראה וגם את השמיעה.
//
//  המפתח עובר ב-header ולא ב-?key= בכתובת: כתובות נכתבות ללוגים,
//  headers לא.
// ============================================================

private const val BASE = "https://generativelanguage.googleapis.com/v1beta/models"

private val log = LoggerFactory.getLogger("GeminiApi")

class GeminiApi(private val apiKey: String) {

    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()

    /** generateContent של [model]. sendAsync + await: ביטול הקורוטינה מבטל גם את הבקשה. */
    suspend fun generate(model: String, body: JsonObject): JsonObject {
        val request = HttpRequest.newBuilder(URI("$BASE/$model:generateContent"))
            .header("Content-Type", "application/json; charset=utf-8")
            .header("x-goog-api-key", apiKey)
            // מודל Pro מפיק משפט ארוך בעד כמה עשרות שניות.
            .timeout(Duration.ofSeconds(60))
            .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
            .build()
        val response = http.sendAsync(request, HttpResponse.BodyHandlers.ofString()).await()
        if (response.statusCode() !in 200..299) {
            throw GeminiException(response.statusCode(), errorMessage(response.body()))
        }
        return Json.parseToJsonElement(response.body()).jsonObject
    }

    /** גוגל עוטפת שגיאות ב-{"error":{"message":...}}. */
    private fun errorMessage(body: String): String =
        runCatching { Json.parseToJsonElement(body).jsonObject["error"]!!.jsonObject["message"]!!.jsonPrimitive.content }
            .getOrDefault(body.take(300))
}

class GeminiException(val status: Int, message: String) : Exception("Gemini $status: $message")

/**
 * לכל מודל יש מכסה יומית משלו (בחינם, ב-gemini-3.8-flash-tts: 100 בקשות ביום).
 * נגמרה באחד — 429 — מנסים את הבא. הדחייה לוקחת רבע שנייה, אז לא שווה לזכור
 * מי נגמר. שגיאה אחרת עוצרת מיד: היא לא תיפתר במודל אחר.
 */
internal suspend fun <T> withQuotaFallback(models: List<String>, call: suspend (model: String) -> T): T {
    var exhausted: GeminiException? = null
    for (model in models) {
        try {
            return call(model)
        } catch (e: GeminiException) {
            if (e.status != 429) throw e
            log.warn("המכסה היומית של {} נגמרה — עוברים למודל הבא", model)
            exhausted = e
        }
    }
    throw checkNotNull(exhausted) { "אין מודלים לנסות" }
}

/** החלקים של התשובה הראשונה — שם נמצא הטקסט או האודיו. */
internal fun partsOf(response: JsonObject): List<JsonObject> =
    response["candidates"]?.jsonArray?.firstOrNull()?.jsonObject
        ?.get("content")?.jsonObject
        ?.get("parts")?.jsonArray
        ?.map { it.jsonObject }
        .orEmpty()
