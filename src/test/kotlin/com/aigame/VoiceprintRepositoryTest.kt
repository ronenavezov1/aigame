package com.aigame

import com.aigame.objects.Address
import com.aigame.objects.Player
import com.aigame.repository.VoiceprintRepository
import com.aigame.state.AppStateManager
import com.aigame.storage.VoiceprintStorage
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VoiceprintRepositoryTest {

    private val dir = Files.createTempDirectory("voice-test")
    private val appStateManager = AppStateManager()

    // שלושה קולות: רוני (1), דנה (2), ומישהו שלא עשה איתחול (3).
    private val recognizer = FakeRecognizer(
        mapOf(
            1.toByte() to floatArrayOf(1f, 0f, 0f),
            2.toByte() to floatArrayOf(0f, 1f, 0f),
            3.toByte() to floatArrayOf(0.3f, 0.3f, 0.9f),
        )
    )
    private val voices = VoiceprintRepository(appStateManager, recognizer, VoiceprintStorage(dir))

    init {
        // זיהוי שוקל רק מי שהגיע.
        appStateManager.updateHostMemory { it.copy(players = listOf("רוני", "דנה", "אורן").associateWith(::Player)) }
    }

    private fun enroll(name: String, who: Int) {
        voices.startEnrollment(name)
        voices.enroll(recording(who))
    }

    @Test
    fun `players who did the setup are recognized - a stranger is not`() {
        enroll("רוני", 1)
        enroll("דנה", 2)

        assertEquals("רוני", voices.identify(recording(1, seconds = 1))?.name)
        assertEquals("דנה", voices.identify(recording(2, seconds = 1))?.name)
        assertNull(voices.identify(recording(3, seconds = 1)))
    }

    @Test
    fun `two similar voices - the guess says who else it might be`() {
        val similar = FakeRecognizer(
            mapOf(1.toByte() to floatArrayOf(1f, 0f, 0f), 4.toByte() to floatArrayOf(0.95f, 0.31f, 0f))
        )
        val close = VoiceprintRepository(appStateManager, similar, VoiceprintStorage(dir))
        close.startEnrollment("רוני"); close.enroll(recording(1))
        close.startEnrollment("אורן"); close.enroll(recording(4))

        val guess = close.identify(recording(1, seconds = 1))
        assertEquals("רוני", guess?.name)
        assertEquals("אורן", guess?.maybe)
    }

    @Test
    fun `a player who did not come is not recognized - even with a voiceprint`() {
        enroll("רוני", 1)
        appStateManager.updateHostMemory { it.copy(players = it.players + ("רוני" to Player("רוני", present = false))) }

        assertNull(voices.identify(recording(1, seconds = 1)))
    }

    @Test
    fun `an unknown woman's voice is the only woman here without a voiceprint`() {
        // רוני עשה איתחול, קרן לא רצתה. WAV אמיתי מתחיל ב-'R' — קול שלא דומה לאף אחד.
        val unknown = FakeRecognizer(mapOf(1.toByte() to floatArrayOf(1f, 0f, 0f), 'R'.code.toByte() to floatArrayOf(0.3f, 0.3f, 0.9f)))
        val voices = VoiceprintRepository(appStateManager, unknown, VoiceprintStorage(dir))
        appStateManager.updateHostMemory {
            it.copy(players = mapOf("רוני" to Player("רוני", address = Address.MALE), "קרן" to Player("קרן", address = Address.FEMALE)))
        }
        voices.startEnrollment("רוני")
        voices.enroll(recording(1))

        val woman = voices.identify(voiceLike(210.0))
        assertEquals("קרן", woman?.name)
        assertEquals(Address.FEMALE, woman?.byVoice)
        // קול של גבר שלא זוהה — כל הגברים פה כבר רשומים, אז אין את מי לנחש.
        assertNull(voices.identify(voiceLike(120.0)))
    }

    @Test
    fun `a short setup recording is refused - and he can try again`() {
        voices.startEnrollment("רוני")
        voices.enroll(recording(1, seconds = 2))

        val state = appStateManager.voiceprintState.value
        assertEquals("רוני", state.enrolling)
        assertNotNull(state.problem)
        assertTrue(state.voiceprints.isEmpty())
    }

    @Test
    fun `a saturated setup recording is refused - it would look like everyone`() {
        voices.startEnrollment("רוני")
        voices.enroll(voiceLike(150.0, seconds = 5.0, volume = 4.0))

        val state = appStateManager.voiceprintState.value
        assertEquals("רוני", state.enrolling)
        assertNotNull(state.problem)
        assertTrue(state.voiceprints.isEmpty())
    }

    @Test
    fun `setup again replaces the old voiceprint - cancel keeps it`() {
        enroll("רוני", 1)
        voices.startEnrollment("רוני")
        voices.cancelEnrollment()
        assertEquals(listOf(1f, 0f, 0f), appStateManager.voiceprintState.value.voiceprints["רוני"])

        enroll("רוני", 2) // מיקרופון חדש
        assertEquals(listOf(0f, 1f, 0f), appStateManager.voiceprintState.value.voiceprints["רוני"])
    }

    @Test
    fun `voiceprints survive a restart`() {
        enroll("רוני", 1)

        val restarted = AppStateManager()
        VoiceprintRepository(restarted, recognizer, VoiceprintStorage(dir)).load()
        assertEquals(setOf("רוני"), restarted.voiceprintState.value.voiceprints.keys)
    }
}
