package com.aigame

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import com.aigame.state.Expression
import com.aigame.state.Utterance
import com.aigame.view.PartyScreen
import com.aigame.view.devices.Microphone
import com.aigame.view.devices.Speaker
import com.aigame.viewmodel.FaceMode
import com.aigame.viewmodel.FaceUi
import com.aigame.viewmodel.UiState
import com.aigame.viewmodel.VoicesUi
import kotlinx.coroutines.awaitCancellation
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test

/** מצייר את המסך בכמה מצבים לקבצי PNG — בלי חלון. רק עם SNAPSHOTS_DIR. */
class FaceSnapshots {

    /** רמקול ש"מנגן" עם פה פתוח, ולא מסיים — כדי שהפה ייתפס בתמונה. */
    private object MouthOpen : Speaker {
        override suspend fun play(wav: ByteArray, onLevel: (Float) -> Unit) {
            onLevel(0.8f)
            awaitCancellation()
        }
    }

    private object NoMicrophone : Microphone {
        override fun start(onLevel: (Float) -> Unit) = false
        override fun stop(): ByteArray? = null
    }

    @Test
    fun render() {
        val out = File(System.getenv("SNAPSHOTS_DIR") ?: return).apply { mkdirs() }
        val speech = "דנה, את בוחרת: שיר במלואו או שלוק. יש לך עשר שניות."
        val cases = listOf(
            "sleeping" to UiState(face = FaceUi(FaceMode.SLEEPING)),
            "listening" to UiState(face = FaceUi(FaceMode.LISTENING)),
            "thinking" to UiState(face = FaceUi(FaceMode.THINKING)),
            "speaking" to UiState(
                face = FaceUi(FaceMode.SPEAKING, Expression.HAPPY),
                subtitle = speech,
                speech = Utterance(speech, audio = byteArrayOf()),
            ),
            "evil" to UiState(face = FaceUi(FaceMode.AWAKE, Expression.EVIL), notice = "אין זיהוי דיבור — F1 כדי להקליד"),
            "laughing" to UiState(face = FaceUi(FaceMode.AWAKE, Expression.LAUGHING)),
            "suspicious" to UiState(face = FaceUi(FaceMode.AWAKE, Expression.SUSPICIOUS)),
            "surprised" to UiState(face = FaceUi(FaceMode.AWAKE, Expression.SURPRISED)),
            "enroll-failed" to UiState(
                face = FaceUi(FaceMode.SLEEPING),
                voices = VoicesUi(enrolling = "רוני", problem = "ההקלטה קצרה מדי (0.4 שניות) — צריך להקריא את כל הטקסט, לפחות 4 שניות."),
            ),
            "enroll-recording" to UiState(face = FaceUi(FaceMode.LISTENING), voices = VoicesUi(enrolling = "רוני", recording = true)),
        )
        for ((name, state) in cases) {
            ImageComposeScene(1280, 720, Density(1f)) {
                PartyScreen(state, NoMicrophone, MouthOpen, onEvent = {}, onToggleFullscreen = {})
            }.use { scene ->
                // שלושה פריימים: הרכבה, אפקטים (הקול מתחיל), ואנימציה שהגיעה ליעד.
                scene.render(0)
                scene.render(500_000_000L)
                val image = scene.render(1_000_000_000L)
                File(out, "$name.png").writeBytes(image.encodeToData(EncodedImageFormat.PNG)!!.bytes)
            }
        }
    }
}
