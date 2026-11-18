package com.aigame

import com.aigame.state.EnvironmentState
import com.aigame.state.HostMemoryState
import com.aigame.state.HostState
import com.aigame.state.MicrophoneState
import com.aigame.state.Phase
import com.aigame.state.Utterance
import com.aigame.state.VoiceprintState
import com.aigame.viewmodel.FaceMode
import com.aigame.viewmodel.toUi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** התרגום מ-states ל-UiState. */
class PartyViewModelTest {

    private fun ui(host: HostState = HostState(), mic: MicrophoneState = MicrophoneState(), env: EnvironmentState = EnvironmentState()) =
        toUi(HostMemoryState(), host, mic, env, VoiceprintState())

    @Test
    fun `listening wins over whatever the host is doing`() {
        assertEquals(FaceMode.LISTENING, ui(HostState(phase = Phase.SPEAKING), MicrophoneState(isListening = true)).face.mode)
    }

    @Test
    fun `there is something to say only while the host is speaking`() {
        val hello = Utterance("שלום", audio = null)
        assertNull(ui(HostState(phase = Phase.AWAKE, utterance = hello)).speech)
        assertEquals(hello, ui(HostState(phase = Phase.SPEAKING, utterance = hello)).speech)
    }

    @Test
    fun `right after the button is released he already shows he is thinking`() {
        assertEquals(FaceMode.THINKING, ui(mic = MicrophoneState(isUnderstanding = true)).face.mode)
    }

    @Test
    fun `while he thinks or talks the button is locked`() {
        assertTrue(ui(HostState(phase = Phase.SLEEPING)).canTalk)
        assertTrue(ui(HostState(phase = Phase.AWAKE)).canTalk)
        assertFalse(ui(HostState(phase = Phase.THINKING)).canTalk)
        assertFalse(ui(HostState(phase = Phase.SPEAKING)).canTalk)
        assertFalse(ui(mic = MicrophoneState(isUnderstanding = true)).canTalk)
    }

    @Test
    fun `during voice setup the talk button is locked - a stray press cannot cut the recording`() {
        val ui = toUi(HostMemoryState(), HostState(), MicrophoneState(), EnvironmentState(), VoiceprintState(enrolling = "רוני"))
        assertFalse(ui.canTalk)
    }

    @Test
    fun `during setup the panel knows if it is recording or processing`() {
        val enrolling = VoiceprintState(enrolling = "רוני")
        fun voices(mic: MicrophoneState) = toUi(HostMemoryState(), HostState(), mic, EnvironmentState(), enrolling).voices

        assertTrue(voices(MicrophoneState(isListening = true)).recording)
        assertTrue(voices(MicrophoneState(isUnderstanding = true)).processing)
        val idle = voices(MicrophoneState())
        assertFalse(idle.recording || idle.processing)
    }

    @Test
    fun `notice lists what is missing`() {
        assertNull(ui().notice)
        assertEquals("אין ANTHROPIC_API_KEY · אין מיקרופון", ui(env = EnvironmentState(isBrainConfigured = false, isMicrophoneAvailable = false)).notice)
    }

    @Test
    fun `a saturated recording shows up in the notice`() {
        assertTrue(ui(env = EnvironmentState(isMicrophoneTooLoud = true)).notice!!.startsWith("המיקרופון חזק מדי"))
    }
}
