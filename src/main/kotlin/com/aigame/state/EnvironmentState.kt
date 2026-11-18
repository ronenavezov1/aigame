package com.aigame.state

// מה מחובר ומה לא. המסך מתרגם את זה להודעה למעלה.
data class EnvironmentState(
    // --- מה הוגדר ---
    val isBrainConfigured: Boolean = true,              // יש ANTHROPIC_API_KEY
    val isSpeechRecognitionConfigured: Boolean = true,  // יש זיהוי דיבור
    val isVoiceprintConfigured: Boolean = true,         // יש מודל טביעת קול
    val isMicrophoneAvailable: Boolean = true,

    // --- מה קרה בזמן ריצה ---
    val brainError: String? = null,  // השגיאה האחרונה של ה-LLM. null = הוא עונה
    val isMicrophoneTooLoud: Boolean = false,  // ההקלטה האחרונה רוויה — הדיבור יצא מעוות
)
