package com.aigame

import com.aiagent.lib.llm.AnthropicLlmClient
import com.aigame.orchestrator.HostInput
import com.aigame.orchestrator.HostOrchestrator
import com.aigame.orchestrator.Llms
import com.aigame.speech.GeminiApi
import com.aigame.speech.GeminiSpeechToText
import com.aigame.speech.GeminiTextToSpeech
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse

/**
 * הקול מקצה לקצה, מול השירותים האמיתיים: Claude כותב משפט ובוחר קול וטון →
 * Gemini מקריא → Gemini מתמלל בחזרה את מה שהוקרא.
 *
 * רק עם LIVE_SPEECH_CHECK=<תיקייה לקבצי ה-WAV>, ועם שני המפתחות ב-.env.
 */
class LiveSpeechCheck {

    @Test
    fun roundTrip() = runBlocking {
        val out = File(System.getenv("LIVE_SPEECH_CHECK") ?: return@runBlocking).apply { mkdirs() }
        val claude = AnthropicLlmClient(apiKey = Env.get("ANTHROPIC_API_KEY")!!)
        val gemini = GeminiApi(Env.get("GOOGLE_API_KEY")!!)
        val context = FakeContext(Llms(claude = claude))

        val inputs = listOf(
            "יאללה, אני מוכן לנאום הכתרה! תן לי הקדמה של מלך",
            "שמעת? רוני הפיל את הכוס על הספה",
        )
        inputs.forEachIndexed { i, text ->
            val output = HostOrchestrator(context).run(HostInput.Heard(text))
            println("=== ${output.style.voice} · ${output.style.delivery}\n    ${output.speech}")

            val started = System.currentTimeMillis()
            val wav = GeminiTextToSpeech(gemini).synthesize(output.speech, output.style)
            println("    הקראה: ${System.currentTimeMillis() - started}ms")
            File(out, "${i + 1}-${output.style.voice}.wav").writeBytes(wav)

            val heard = GeminiSpeechToText(gemini).transcribe(wav, hints = emptyList())
            println("    תמלול בחזרה: $heard")
            assertFalse(heard.isNullOrBlank())
        }
    }
}
