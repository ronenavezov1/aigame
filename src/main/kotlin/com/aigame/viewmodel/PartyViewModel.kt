package com.aigame.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aigame.repository.AppRepository
import com.aigame.state.EnvironmentState
import com.aigame.state.HostMemoryState
import com.aigame.state.HostState
import com.aigame.state.MicrophoneState
import com.aigame.state.Phase
import com.aigame.state.VoiceprintState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// ============================================================
//  ה-ViewModel. בלי מצב משלו:
//
//    onEvent   אירוע מהמסך → פונקציה ברפוזיטורי.
//    state     ה-states של AppStateManager, מתורגמים ל-UiState.
// ============================================================

class PartyViewModel(private val appRepository: AppRepository) : ViewModel() {

    private val states = appRepository.appStateManager

    val state: StateFlow<UiState> =
        combine(states.hostMemoryState, states.hostState, states.microphoneState, states.environmentState, states.voiceprintState, ::toUi)
            .stateIn(
                viewModelScope,
                SharingStarted.Eagerly,
                toUi(
                    states.hostMemoryState.value,
                    states.hostState.value,
                    states.microphoneState.value,
                    states.environmentState.value,
                    states.voiceprintState.value,
                ),
            )

    fun onEvent(event: UiEvent) = when (event) {
        UiEvent.TalkPressed -> appRepository.microphone.startListening()
        is UiEvent.TalkReleased -> appRepository.microphone.stopListening(event.recording)
        UiEvent.MicrophoneUnavailable -> appRepository.microphone.microphoneUnavailable()
        UiEvent.WakeNow -> appRepository.host.wakeNow()
        is UiEvent.Typed -> appRepository.host.hear(event.text)
        UiEvent.SpeechFinished -> appRepository.host.speechFinished()
        is UiEvent.EnrollVoice -> appRepository.voiceprints.startEnrollment(event.name)
        UiEvent.CancelEnrollment -> appRepository.voiceprints.cancelEnrollment()
        is UiEvent.SetPresent -> appRepository.memory.setPresent(event.name, event.present)
    }
}

private val CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())

/** טהור: אותו קלט, אותו פלט. */
internal fun toUi(
    memory: HostMemoryState,
    host: HostState,
    mic: MicrophoneState,
    env: EnvironmentState,
    voices: VoiceprintState,
): UiState = UiState(
    face = FaceUi(
        mode = when {
            mic.isListening -> FaceMode.LISTENING
            mic.isUnderstanding || host.phase == Phase.THINKING -> FaceMode.THINKING
            host.phase == Phase.SPEAKING -> FaceMode.SPEAKING
            host.phase == Phase.AWAKE -> FaceMode.AWAKE
            else -> FaceMode.SLEEPING
        },
        expression = host.expression,
    ),
    subtitle = host.subtitle,
    speech = host.utterance.takeIf { host.phase == Phase.SPEAKING },
    notice = noticeOf(env),
    players = memory.players.keys.toList(),
    recent = memory.conversation.takeLast(12).map { "${it.who}: ${it.text}" },
    nextWake = memory.clock.nextWakeAt?.let { CLOCK.format(Instant.ofEpochMilli(it)) },
    // באיתחול מקליטים רק בכפתורים של הפאנל — רווח ולחיצה על הפרצוף לא יקטעו את ההקראה.
    canTalk = voices.enrolling == null && !mic.isUnderstanding && host.phase != Phase.THINKING && host.phase != Phase.SPEAKING,
    voices = VoicesUi(
        players = memory.players.values.map { VoiceRow(it.name, enrolled = it.name in voices.voiceprints, present = it.present) },
        enrolling = voices.enrolling,
        recording = voices.enrolling != null && mic.isListening,
        processing = voices.enrolling != null && mic.isUnderstanding,
        problem = voices.problem,
    ),
)

/** מה לא מחובר, בשורה אחת. null = הכל תקין. */
private fun noticeOf(env: EnvironmentState): String? = listOfNotNull(
    "אין ANTHROPIC_API_KEY".takeUnless { env.isBrainConfigured },
    "אין זיהוי דיבור — F1 כדי להקליד".takeUnless { env.isSpeechRecognitionConfigured },
    "אין מיקרופון".takeUnless { env.isMicrophoneAvailable },
    "המיקרופון חזק מדי, הדיבור יוצא מעוות — הנמך את עוצמת הקלט בהגדרות הצליל".takeIf { env.isMicrophoneTooLoud },
    "אין מודל טביעת קול — כולם user".takeUnless { env.isVoiceprintConfigured },
    env.brainError?.let { "המוח לא עונה: ${it.take(120)}" },
).joinToString(" · ").ifEmpty { null }
