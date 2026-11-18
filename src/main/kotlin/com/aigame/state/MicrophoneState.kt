package com.aigame.state

data class MicrophoneState(
    // מישהו מחזיק את הכפתור עכשיו. ההקלטה עצמה — במסך.
    val isListening: Boolean = false,
    // הכפתור שוחרר, וההקלטה הופכת לטקסט ולשם — לפני שהמוח בכלל התחיל.
    val isUnderstanding: Boolean = false,
)
