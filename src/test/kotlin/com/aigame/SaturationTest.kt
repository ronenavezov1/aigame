package com.aigame

import com.aigame.speech.isSaturated
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SaturationTest {

    @Test
    fun `a normal recording is not saturated`() {
        assertFalse(isSaturated(voiceLike(150.0)))
        assertFalse(isSaturated(voiceLike(150.0, volume = 0.0)))
    }

    @Test
    fun `a microphone turned up too far - saturated`() {
        assertTrue(isSaturated(voiceLike(150.0, volume = 4.0)))
    }

    @Test
    fun `something that is not a WAV - not saturated, and no crash`() {
        assertFalse(isSaturated(recording(1)))
    }
}
