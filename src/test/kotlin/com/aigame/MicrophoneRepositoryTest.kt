package com.aigame

import com.aigame.objects.HostEvent
import com.aigame.objects.Player
import com.aigame.repository.MicrophoneRepository
import com.aigame.repository.VoiceprintRepository
import com.aigame.speech.NoSpeechToText
import com.aigame.speech.SpeechToText
import com.aigame.state.AppStateManager
import com.aigame.storage.VoiceprintStorage
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MicrophoneRepositoryTest {

    private val dir = Files.createTempDirectory("mic-test")
    private val appStateManager = AppStateManager()
    private val voices = VoiceprintRepository(
        appStateManager,
        FakeRecognizer(mapOf(1.toByte() to floatArrayOf(1f, 0f))),
        VoiceprintStorage(dir),
    )

    /** מה המארח שמע. */
    private fun TestScope.events(): List<HostEvent> {
        val events = mutableListOf<HostEvent>()
        backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) { appStateManager.hostEventFlow.collect { events += it } }
        return events
    }

    private fun TestScope.microphone(stt: SpeechToText = NoSpeechToText) =
        MicrophoneRepository(appStateManager, stt, voices, backgroundScope, dir)

    @Test
    fun `without speech recognition - the recording is kept and the host hears he misheard`() = runTest {
        val events = events()
        val microphone = microphone()

        microphone.startListening()
        assertTrue(appStateManager.microphoneState.value.isListening)

        microphone.stopListening(recording = byteArrayOf(1, 2, 3))
        runCurrent() // לא advanceUntilIdle: הוא לא מריץ עבודה ב-backgroundScope

        assertFalse(appStateManager.microphoneState.value.isListening)
        assertEquals(listOf<HostEvent>(HostEvent.Misheard), events)
        assertTrue(Files.exists(dir.resolve("last-recording.wav")))
    }

    @Test
    fun `the host hears what was said - and who the voiceprint thinks said it`() = runTest {
        appStateManager.updateHostMemory { it.copy(players = mapOf("רוני" to Player("רוני"))) }
        voices.startEnrollment("רוני")
        voices.enroll(recording(1))
        val events = events()
        val microphone = microphone(stt = object : SpeechToText {
            override val isConfigured = true
            override suspend fun transcribe(wav: ByteArray, hints: List<String>) = "עוד בירה"
        })

        microphone.startListening()
        microphone.stopListening(recording(1, seconds = 1))
        runCurrent()

        val heard = events.single() as HostEvent.Heard
        assertEquals("עוד בירה", heard.text)
        assertEquals("רוני", heard.speaker?.name)
    }

    @Test
    fun `during setup the recording becomes a voiceprint - the host hears nothing`() = runTest {
        val events = events()
        val microphone = microphone()
        voices.startEnrollment("רוני")

        microphone.startListening()
        microphone.stopListening(recording(1))
        runCurrent()

        assertTrue(events.isEmpty())
        assertEquals(setOf("רוני"), appStateManager.voiceprintState.value.voiceprints.keys)
    }

    @Test
    fun `a saturated recording - the screen warns until a clean one comes`() = runTest {
        val microphone = microphone()

        microphone.startListening()
        microphone.stopListening(voiceLike(150.0, volume = 4.0))
        runCurrent()
        assertTrue(appStateManager.environmentState.value.isMicrophoneTooLoud)

        microphone.startListening()
        microphone.stopListening(voiceLike(150.0))
        runCurrent()
        assertFalse(appStateManager.environmentState.value.isMicrophoneTooLoud)
    }

    @Test
    fun `screen without a microphone - shown as missing`() = runTest {
        microphone().microphoneUnavailable()
        assertFalse(appStateManager.environmentState.value.isMicrophoneAvailable)
    }
}
