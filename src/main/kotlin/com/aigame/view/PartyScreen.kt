package com.aigame.view

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aigame.view.devices.Microphone
import com.aigame.view.devices.Speaker
import com.aigame.viewmodel.FaceMode
import com.aigame.viewmodel.PartyViewModel
import com.aigame.viewmodel.UiEvent
import com.aigame.viewmodel.UiState

// ============================================================
//  המסך — כל מה שבצד של המשתמש: מסך, מיקרופון, רמקול.
//  מאזין ל-UiState ומעדכן תמונה (הפרצוף), טקסט (הכתובית) וקול
//  (SpeechPlayer). כל פעולה של המשתמש יוצאת כ-UiEvent.
//
//  הכפתור: רווח, PageUp או PageDown (מה שקליקר של מצגות שולח),
//  או לחיצה ארוכה בעכבר על הפרצוף. מחזיקים — המיקרופון מקליט.
//  עוזבים — ההקלטה יוצאת ב-TalkReleased.
//
//    F1    פאנל פיתוח: הקלדה במקום דיבור, יומן, "העֵר אותו"
//    ⚙ / F2  הגדרות: טביעות קול — איתחול לכל שחקן, כדי שהמארח ידע מי מדבר
//
//  כשהוא חושב או מדבר הכפתור נעול: אסור להפריע לו.
//    F11   מסך מלא
//
//  מה שחי כאן ב-remember הוא רק מצב של התצוגה עצמה: האם הפאנל
//  פתוח, ועוצמות הקול של המיקרופון והרמקול.
// ============================================================

private val TALK_KEYS = setOf(Key.Spacebar, Key.PageUp, Key.PageDown)

@Composable
fun PartyScreen(viewModel: PartyViewModel, microphone: Microphone, speaker: Speaker, onToggleFullscreen: () -> Unit) {
    val state by viewModel.state.collectAsState()
    PartyScreen(state, microphone, speaker, viewModel::onEvent, onToggleFullscreen)
}

@Composable
fun PartyScreen(
    state: UiState,
    microphone: Microphone,
    speaker: Speaker,
    onEvent: (UiEvent) -> Unit,
    onToggleFullscreen: () -> Unit,
) {
    var devOpen by remember { mutableStateOf(false) }
    var voicesOpen by remember { mutableStateOf(false) }
    var micLevel by remember { mutableFloatStateOf(0f) }
    var mouthOpen by remember { mutableFloatStateOf(0f) }
    val focus = remember { FocusRequester() }
    // נקרא בזמן הלחיצה, לא בזמן הציור — גם מתוך pointerInput שנבנה פעם אחת.
    val canTalk by rememberUpdatedState(state.canTalk)

    // האם הלחיצה הנוכחית על הכפתור היא שהתחילה הקלטה — רק אותה עוזבים.
    var talking by remember { mutableStateOf(false) }

    val startMic: () -> Boolean = {
        val ok = microphone.start { micLevel = it }
        onEvent(if (ok) UiEvent.TalkPressed else UiEvent.MicrophoneUnavailable)
        ok
    }
    /** עוצר את המיקרופון. keep = false: ההקלטה נזרקת (ביטול). */
    val stopMic = { keep: Boolean ->
        val recording = microphone.stop()
        micLevel = 0f
        onEvent(UiEvent.TalkReleased(recording.takeIf { keep }))
    }

    // הכפתור (רווח, קליקר, או הפרצוף). press נקרא שוב ושוב כשמקש מוחזק.
    // כשהוא חושב, מדבר, או באמצע איתחול — לא מקליטים בכלל: אסור להפריע.
    val press = { if (canTalk && !talking) talking = startMic() }
    val release = {
        if (talking) {
            talking = false
            stopMic(true)
        }
    }

    // האיתחול מקליט מהכפתורים של הפאנל: "איתחול" מתחיל, "שמור" עוצר ושולח.
    val enroll = EnrollActions(
        start = { name ->
            onEvent(UiEvent.EnrollVoice(name))
            startMic()
        },
        save = { stopMic(true) },
        retry = { startMic() },
        cancel = {
            stopMic(false)
            onEvent(UiEvent.CancelEnrollment)
        },
    )

    // הפוקוס חוזר לשורש כשהפאנל נסגר, אחרת הכפתור לא מגיע.
    LaunchedEffect(devOpen) { if (!devOpen) focus.requestFocus() }

    SpeechPlayer(
        utterance = state.speech,
        speaker = speaker,
        onLevel = { mouthOpen = it },
        onFinished = { onEvent(UiEvent.SpeechFinished) },
    )

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Box(
            Modifier
                .fillMaxSize()
                .background(FaceBackground)
                .onPreviewKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (e.key) {
                        Key.F1 -> { devOpen = !devOpen; true }
                        Key.F2 -> { voicesOpen = !voicesOpen; true }
                        Key.F11 -> { onToggleFullscreen(); true }
                        else -> false
                    }
                }
                .focusRequester(focus)
                .focusable()
                .onKeyEvent { e -> talkKey(e, devOpen, press, release) },
        ) {
            FaceView(
                face = state.face,
                micLevel = micLevel,
                mouthOpen = mouthOpen,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(onPress = {
                            press()
                            tryAwaitRelease()
                            release()
                        })
                    },
            )

            state.notice?.let {
                Text(
                    it,
                    color = Color(0xFFFFB86B),
                    fontSize = 16.sp,
                    modifier = Modifier.align(Alignment.TopCenter).padding(16.dp),
                )
            }

            // הכתובית — ובזמן שהוא חושב, "חושב…", כדי שיהיה ברור שהוא לא נתקע.
            val caption = state.subtitle ?: "חושב…".takeIf { state.face.mode == FaceMode.THINKING }
            caption?.let {
                Text(
                    it,
                    color = if (state.subtitle != null) Color.White else Color(0xFF8AF8FF),
                    fontSize = 38.sp,
                    lineHeight = 48.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 48.dp, vertical = 40.dp),
                )
            }

            if (devOpen) {
                DevPanel(state, onEvent, Modifier.align(Alignment.TopStart))
            }

            // באמצע איתחול הפאנל נשאר פתוח — שם הטקסט להקראה.
            if (voicesOpen || state.voices.enrolling != null) {
                VoicePanel(
                    voices = state.voices,
                    micLevel = micLevel,
                    onEvent = onEvent,
                    enroll = enroll,
                    onClose = { voicesOpen = false },
                    modifier = Modifier.align(Alignment.TopEnd),
                )
            } else {
                // לא לוקח פוקוס: ככה רווח נשאר הכפתור של המיקרופון.
                TextButton(
                    onClick = { voicesOpen = true },
                    modifier = Modifier.align(Alignment.TopEnd).padding(12.dp).focusProperties { canFocus = false },
                ) { Text("⚙ הגדרות", fontSize = 18.sp, color = Color(0xFFB8BCC8)) }
            }
        }
    }
}

/** הקש של הכפתור. כשהפאנל פתוח רווח שייך להקלדה, ורק הקליקר עובד. */
private fun talkKey(e: KeyEvent, devOpen: Boolean, press: () -> Unit, release: () -> Unit): Boolean {
    if (e.key !in TALK_KEYS) return false
    if (devOpen && e.key == Key.Spacebar) return false
    when (e.type) {
        KeyEventType.KeyDown -> press()
        KeyEventType.KeyUp -> release()
        else -> return false
    }
    return true
}
