package com.aigame.viewmodel

import com.aigame.state.Expression
import com.aigame.state.Utterance

// ============================================================
//  מה שהתצוגה רואה, ומה שהיא יכולה לבקש.
//
//  UiState הוא תמונה לקריאה בלבד שה-ViewModel גוזר מה-states של
//  AppStateManager. אין בו שום דבר שאי אפשר לחשב מחדש משם.
// ============================================================

data class UiState(
    val face: FaceUi = FaceUi(),
    val subtitle: String? = null,
    /** מה להקריא עכשיו. null = המארח לא מדבר. */
    val speech: Utterance? = null,
    val notice: String? = null,
    val players: List<String> = emptyList(),
    /** השורות האחרונות בשיחה, לפאנל הפיתוח. */
    val recent: List<String> = emptyList(),
    /** מתי ההתעוררות הבאה, HH:mm:ss. */
    val nextWake: String? = null,
    val voices: VoicesUi = VoicesUi(),
    /** אפשר ללחוץ ולדבר. false = הוא חושב או מדבר — אסור להפריע לו. */
    val canTalk: Boolean = true,
)

/** פאנל טביעות הקול (F2): מי כבר עשה איתחול, ומי מקריא עכשיו. */
data class VoicesUi(
    val players: List<VoiceRow> = emptyList(),
    /** מי מקריא עכשיו את טקסט האיתחול. null = אף אחד. */
    val enrolling: String? = null,
    /** באמצע איתחול: מקליטים עכשיו. */
    val recording: Boolean = false,
    /** באמצע איתחול: ההקלטה נגמרה, והטביעה מחושבת. */
    val processing: Boolean = false,
    val problem: String? = null,
)

/** שורה בהגדרות: שחקן, האם הגיע, והאם יש לו טביעת קול. */
data class VoiceRow(val name: String, val enrolled: Boolean, val present: Boolean = true)

data class FaceUi(
    val mode: FaceMode = FaceMode.SLEEPING,
    val expression: Expression = Expression.NEUTRAL,
)

/** מה הפרצוף עושה. "מקשיב" גובר על השאר — זה מה שמי שמחזיק את הכפתור צריך לראות. */
enum class FaceMode { SLEEPING, AWAKE, LISTENING, THINKING, SPEAKING }

sealed interface UiEvent {
    /** הכפתור נלחץ והמסך התחיל להקליט. */
    data object TalkPressed : UiEvent

    /** הכפתור שוחרר. recording = WAV, או null אם הלחיצה הייתה קצרה מדי. */
    class TalkReleased(val recording: ByteArray?) : UiEvent

    /** הכפתור נלחץ, אבל אין מיקרופון. */
    data object MicrophoneUnavailable : UiEvent

    data object WakeNow : UiEvent

    /** הקלדה בפאנל — עוברת בדיוק כמו משפט שנאמר במיקרופון. */
    data class Typed(val text: String) : UiEvent

    /** המסך סיים להקריא את speech. */
    data object SpeechFinished : UiEvent

    /** "איתחול" ליד שחקן: ההקלטה הבאה תהיה טביעת הקול שלו. */
    data class EnrollVoice(val name: String) : UiEvent

    data object CancelEnrollment : UiEvent

    /** "נוכח" בהגדרות: הגיע הערב, או לא. */
    data class SetPresent(val name: String, val present: Boolean) : UiEvent
}
