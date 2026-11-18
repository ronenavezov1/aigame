package com.aigame.speech

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.util.Base64

// ============================================================
//  אודיו → טקסט בעברית, דרך Gemini.
//
//  שולחים את ההקלטה עם הוראה "תמלל", ומקבלים את הטקסט. שמות השחקנים
//  נכנסים להוראה — הם מה שזיהוי דיבור הכי טועה בו.
//
//  gemini-3.8-flash: בהשוואה על הקלטות שלנו רק הוא תמלל נכון ("כלוא",
//  "מעבד") — 2.5-flash ניחש מילים דומות, ומודל התמלול הייעודי
//  (3.5-transcribe) טעה עוד יותר. נגמרה המכסה שלו — 2.5-flash.
// ============================================================

private const val BACKUP_MODEL = "gemini-2.5-flash"

class GeminiSpeechToText(
    private val gemini: GeminiApi,
    private val model: String = "gemini-3.8-flash",
) : SpeechToText {

    override val isConfigured = true

    override suspend fun transcribe(wav: ByteArray, hints: List<String>): String? {
        val instruction = buildString {
            append("תמלל במדויק את מה שנאמר בהקלטה, בעברית. החזר רק את הטקסט, בלי שום הסבר. ")
            append("אם לא נאמר כלום, או שאי אפשר להבין — החזר שורה ריקה.")
            if (hints.isNotEmpty()) append(" שמות שעשויים להופיע: ${hints.joinToString(", ")}.")
        }
        val response = withQuotaFallback(listOf(model, BACKUP_MODEL).distinct()) { m -> gemini.generate(m, request(m, instruction, wav)) }
        return partsOf(response)
            .mapNotNull { it["text"]?.jsonPrimitive?.content }
            .joinToString("")
            .trim()
            .takeIf { it.isNotEmpty() }
    }

    internal fun request(model: String, instruction: String, wav: ByteArray): JsonObject = buildJsonObject {
        putJsonArray("contents") {
            add(buildJsonObject {
                putJsonArray("parts") {
                    add(buildJsonObject { put("text", instruction) })
                    add(buildJsonObject {
                        putJsonObject("inlineData") {
                            put("mimeType", "audio/wav")
                            put("data", Base64.getEncoder().encodeToString(wav))
                        }
                    })
                }
            })
        }
        // רמת חשיבה יש רק בדור 3. "low" — אותו תמלול, שנייה וחצי מהר יותר.
        if (model.startsWith("gemini-3")) {
            putJsonObject("generationConfig") { putJsonObject("thinkingConfig") { put("thinkingLevel", "low") } }
        }
    }
}
