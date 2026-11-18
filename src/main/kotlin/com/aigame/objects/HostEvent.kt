package com.aigame.objects

/** מה שמעיר את המארח. כל אירוע מטופל לפי הסדר, אחד אחרי השני. */
sealed interface HostEvent {
    /** מישהו לחץ על הכפתור ודיבר (או הקליד בפאנל). speaker — מה זיהוי הקול חושב. */
    data class Heard(val text: String, val speaker: SpeakerGuess? = null) : HostEvent

    /** מישהו דיבר, אבל לא הבנו מה — אין זיהוי דיבור, או שלא זוהה כלום. */
    data object Misheard : HostEvent

    /** הטיימר האקראי — המארח קם לבד לתת משימה. */
    data object WakeUp : HostEvent
}
