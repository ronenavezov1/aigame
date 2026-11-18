package com.aigame.view

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.aigame.state.Utterance
import com.aigame.view.devices.Speaker
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * הקול של המסך: כשיש [utterance] — מנגן אותו ברמקול ומדווח עוצמה (לפה).
 * בלי אודיו — הפה זז בקצב של דיבור, והכתובית עושה את העבודה.
 * בסוף — [onFinished]. אם המשפט מתחלף או נעלם באמצע, הניגון נעצר.
 */
@Composable
fun SpeechPlayer(
    utterance: Utterance?,
    speaker: Speaker,
    onLevel: (Float) -> Unit,
    onFinished: () -> Unit,
) {
    LaunchedEffect(utterance) {
        if (utterance == null) return@LaunchedEffect
        try {
            val audio = utterance.audio
            if (audio != null) speaker.play(audio, onLevel) else mimeSpeech(utterance.text, onLevel)
        } finally {
            onLevel(0f)
        }
        onFinished()
    }
}

/** בלי קול: הפה נפתח על אותיות ונסגר על רווחים וסימני פיסוק, בקצב של דיבור. */
private suspend fun mimeSpeech(text: String, onLevel: (Float) -> Unit) {
    for (c in text) {
        onLevel(if (c.isLetterOrDigit()) Random.nextDouble(0.35, 1.0).toFloat() else 0.05f)
        delay(65)
    }
}
