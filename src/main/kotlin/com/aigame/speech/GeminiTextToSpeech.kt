package com.aigame.speech

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.io.ByteArrayOutputStream
import java.util.Base64
import javax.sound.sampled.AudioFileFormat
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem

// ============================================================
//  טקסט → אודיו בעברית, דרך מודלי ההקראה של Gemini.
//
//  שולחים הוראת משחק + טקסט (בפרקים נפרדים — ttsPrompt), ומקבלים אודיו.
//  כל SpeechVoice הוא מודל + קול: CHARON_PRO רץ על Pro (איכותי יותר,
//  איטי יותר), השאר על Flash. נגמרה המכסה היומית של מודל — עוברים לאחר.
// ============================================================

private const val QUICK_MODEL = "gemini-3.8-flash-tts"
private const val QUICK_BACKUP = "gemini-2.5-flash-preview-tts"
private const val RICH_MODEL = "gemini-2.5-pro-preview-tts"

// לכל מודל מכסה יומית משלו. נגמרה בראשון — עוברים לבא (withQuotaFallback).
private val QUICK_MODELS = listOf(QUICK_MODEL, QUICK_BACKUP, RICH_MODEL)
private val RICH_MODELS = listOf(RICH_MODEL, QUICK_MODEL, QUICK_BACKUP)

// Gemini מחזיר PCM של 16 ביט, מונו, 24kHz — לפעמים בלי כותרת WAV.
private val PCM_FORMAT = AudioFormat(24_000f, 16, 1, true, false)

class GeminiTextToSpeech(private val gemini: GeminiApi) : TextToSpeech {

    override suspend fun synthesize(text: String, style: SpeechStyle): ByteArray {
        val (models, voiceName) = when (style.voice) {
            SpeechVoice.CHARON_PRO -> RICH_MODELS to "Charon"
            SpeechVoice.PUCK -> QUICK_MODELS to "Puck"
            SpeechVoice.FENRIR -> QUICK_MODELS to "Fenrir"
        }
        val response = withQuotaFallback(models) { model -> gemini.generate(model, request(text, voiceName, style.delivery)) }
        val audio = partsOf(response).firstNotNullOfOrNull { it["inlineData"]?.jsonObject }
            ?.get("data")?.jsonPrimitive?.content
            ?: error("אין אודיו בתשובה")
        return asWav(Base64.getDecoder().decode(audio))
    }

    private fun request(text: String, voice: String, delivery: String): JsonObject = buildJsonObject {
        putJsonArray("contents") {
            add(buildJsonObject { putJsonArray("parts") { add(buildJsonObject { put("text", ttsPrompt(text, delivery)) }) } })
        }
        putJsonObject("generationConfig") {
            putJsonArray("responseModalities") { add("AUDIO") }
            putJsonObject("speechConfig") {
                putJsonObject("voiceConfig") { putJsonObject("prebuiltVoiceConfig") { put("voiceName", voice) } }
            }
        }
    }

    /**
     * הוראת המשחק והטקסט בשני פרקים נפרדים, עם כותרות.
     *
     * כשהם היו צמודים ("Say this in Hebrew, cheeky:\n<טקסט>"), המודל הקריא
     * גם את ההוראה עצמה — בבדיקה מולו זה הוסיף כ-3 שניות דיבור כמעט בכל
     * פעם. עם כותרות הוא מקריא רק את מה שתחת TRANSCRIPT.
     */
    internal fun ttsPrompt(text: String, delivery: String): String = buildString {
        append("### DIRECTOR'S NOTES\nLanguage: Hebrew.")
        // שורה אחת, בלי כותרות משלה — כדי שלא תיפתח בטעות פרק חדש.
        val notes = delivery.replace(Regex("[\\r\\n#]+"), " ").trim().take(200)
        if (notes.isNotEmpty()) append(" Delivery: $notes.")
        append("\n\n### TRANSCRIPT\n$text")
    }

    /** הרמקול קורא WAV. אם הגיע PCM גולמי — עוטפים אותו בכותרת. */
    private fun asWav(audio: ByteArray): ByteArray {
        if (audio.size >= 4 && String(audio, 0, 4) == "RIFF") return audio
        val wav = ByteArrayOutputStream()
        AudioSystem.write(
            AudioInputStream(audio.inputStream(), PCM_FORMAT, audio.size / PCM_FORMAT.frameSize.toLong()),
            AudioFileFormat.Type.WAVE,
            wav,
        )
        return wav.toByteArray()
    }
}
