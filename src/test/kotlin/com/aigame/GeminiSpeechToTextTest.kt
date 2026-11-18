package com.aigame

import com.aigame.speech.GeminiApi
import com.aigame.speech.GeminiSpeechToText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** מה שנשלח למודל התמלול. */
class GeminiSpeechToTextTest {

    private val stt = GeminiSpeechToText(GeminiApi("לא בשימוש"))

    @Test
    fun `generation 3 thinks low - the backup model gets no thinking level it does not know`() {
        val new = stt.request("gemini-3.8-flash", "תמלל", byteArrayOf(1))
        val backup = stt.request("gemini-2.5-flash", "תמלל", byteArrayOf(1))

        assertEquals("{\"thinkingConfig\":{\"thinkingLevel\":\"low\"}}", new["generationConfig"].toString())
        assertFalse("generationConfig" in backup)
    }
}
