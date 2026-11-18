package com.aigame

import com.aigame.objects.Address
import com.aigame.speech.pitchHz
import com.aigame.speech.voiceGender
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VoicePitchTest {

    @Test
    fun `measures how high a voice is`() {
        val low = pitchHz(voiceLike(120.0))!!
        val high = pitchHz(voiceLike(220.0))!!
        assertTrue(abs(low - 120) < 6, "$low")
        assertTrue(abs(high - 220) < 10, "$high")
    }

    @Test
    fun `a woman's voice, a man's voice, and in between - not sure`() {
        assertEquals(Address.FEMALE, voiceGender(pitchHz(voiceLike(210.0))))
        assertEquals(Address.MALE, voiceGender(pitchHz(voiceLike(125.0))))
        assertNull(voiceGender(168f))
    }

    @Test
    fun `silence has no pitch`() {
        assertNull(pitchHz(voiceLike(200.0, volume = 0.0)))
    }
}
