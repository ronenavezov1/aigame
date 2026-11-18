package com.aigame

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aigame.repository.AppRepository
import com.aigame.view.PartyScreen
import com.aigame.view.devices.JavaSoundMicrophone
import com.aigame.view.devices.JavaSoundSpeaker
import com.aigame.viewmodel.PartyViewModel

// ============================================================
//  נקודת הכניסה.
//
//  השכבות, מלמעלה למטה:
//
//    view/         צד המשתמש: המסך, ו-devices/ — מיקרופון ורמקול
//    viewmodel/    מתרגם את ה-states ל-UiState, ואירועים לפונקציות ברפוזיטורי
//    repository/   חסרי מצב: מפעילים את שירותי הדיבור ואת המנצח, ומעדכנים states
//    state/        AppStateManager וה-States — מקור האמת היחיד
//    storage/      הזיכרון על הדיסק (data/) — מה שמחזיק את המסיבה בין הפעלות
//    orchestrator/ הסוכן (HostOrchestrator): קלט → פלט. רץ כמו בארגז חול —
//                  כל מה שיש לו מגיע ב-context: LLM-ים, זיכרון ויכולות
//    objects/      אובייקטי-תחום: שחקן, שורת שיחה, משימה, אירוע
//    speech/       קול ↔ טקסט (גוגל). שירות, לא סוכן
//
//  הקובץ הזה בונה את AppRepository ואת הצד של המשתמש: חלון, מיקרופון ורמקול.
// ============================================================

fun main() {
    val app = AppRepository.create()
    val microphone = JavaSoundMicrophone()
    val speaker = JavaSoundSpeaker()

    // גם סגירת חלון, גם Ctrl+C בטרמינל — שתיהן עוברות כאן. אין מה לשמור:
    // כל שינוי בזיכרון כבר נכתב לקובץ, אז גם נפילת חשמל לא מאבדת כלום.
    Runtime.getRuntime().addShutdownHook(Thread(app::close, "stop-on-exit"))

    application {
        val windowState = rememberWindowState(placement = WindowPlacement.Maximized)

        Window(onCloseRequest = ::exitApplication, state = windowState, title = HOST_NAME) {
            val viewModel = viewModel { PartyViewModel(app) }

            MaterialTheme(colorScheme = darkColorScheme()) {
                PartyScreen(
                    viewModel = viewModel,
                    microphone = microphone,
                    speaker = speaker,
                    onToggleFullscreen = {
                        windowState.placement =
                            if (windowState.placement == WindowPlacement.Fullscreen) WindowPlacement.Maximized
                            else WindowPlacement.Fullscreen
                    },
                )
            }
        }
    }
}
