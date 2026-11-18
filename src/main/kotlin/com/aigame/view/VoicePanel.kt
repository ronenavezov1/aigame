package com.aigame.view

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aigame.HOST_NAME
import com.aigame.viewmodel.UiEvent
import com.aigame.viewmodel.VoicesUi
import kotlinx.coroutines.delay

// מה שכל שחקן מקריא באיתחול: כ-15 שניות, עם הרבה צלילים שונים — ככה הטביעה טובה יותר.
private const val ENROLL_TEXT =
    "אני מצהיר בזאת, בפני השופט העליון של הבית, שאני כאן מרצוני החופשי. " +
        "אני יודע שכל משימה אפשר להמיר בצ'ייסר, ושכל צ'ייסר חייב להישתות. " +
        "אני מבטיח לדבר אל " + HOST_NAME + " בכבוד, לא להתחכם, ולא לבקש ממנו לעשות לי שיעורי בית. " +
        "ואם אשכח — שיזכיר לי."

/** מה שכפתורי האיתחול עושים: "איתחול" מתחיל להקליט, "שמור" עוצר ושולח, "ביטול" זורק. */
class EnrollActions(
    val start: (name: String) -> Unit,
    val save: () -> Unit,
    val retry: () -> Unit,
    val cancel: () -> Unit,
)

/**
 * ההגדרות (⚙ או F2): לכל שחקן — "נוכח" (הגיע הערב?), וטביעת הקול: מי כבר
 * עשה איתחול, וכפתור איתחול — גם למי שכבר עשה, למשל אחרי שהחליפו מיקרופון.
 *
 * איתחול: לוחצים "איתחול" — ההקלטה מתחילה מיד. השחקן מקריא, ולוחצים
 * "סיימתי — שמור". פס העוצמה מראה כל הזמן שהמיקרופון באמת קולט.
 *
 * הכפתורים כאן לא לוקחים פוקוס: ככה רווח נשאר הכפתור של המיקרופון.
 */
@Composable
fun VoicePanel(
    voices: VoicesUi,
    micLevel: Float,
    onEvent: (UiEvent) -> Unit,
    enroll: EnrollActions,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val noFocus = Modifier.focusProperties { canFocus = false }

    Surface(
        modifier = modifier.width(440.dp).padding(16.dp),
        color = Color(0xF01A1A24),
        // טקסט בהיר: בלי זה הוא יוצא שחור על הרקע הכהה.
        contentColor = Color(0xFFE8EAF0),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            Modifier.padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val enrolling = voices.enrolling
            if (enrolling != null) {
                // באמצע איתחול אין "סגור": רק לשמור, או לבטל.
                Enrollment(enrolling, voices, micLevel, enroll, noFocus)
                return@Column
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("הגדרות — מי כאן, וטביעות קול", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = onClose, modifier = noFocus) { Text("✕ סגור") }
            }
            Text(
                "סמנו מי הגיע — מי שלא, $HOST_NAME לא יפנה אליו. כל שחקן עושה איתחול קול פעם אחת, וככה $HOST_NAME יודע מי מדבר.",
                color = Color(0xFFB8BCC8),
            )

            if (voices.players.isEmpty()) Text("אין עדיין שחקנים. מי שמציג את עצמו למארח — יופיע כאן.")
            voices.players.forEach { row ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Checkbox(
                        checked = row.present,
                        onCheckedChange = { onEvent(UiEvent.SetPresent(row.name, it)) },
                        modifier = noFocus,
                    )
                    Text(
                        if (row.present) row.name else "${row.name} (לא הגיע)",
                        color = if (row.present) Color.Unspecified else Color(0xFF7A7F8C),
                        modifier = Modifier.weight(1f),
                    )
                    if (row.enrolled) {
                        Text("✓ רשום", color = Color(0xFF7BD88F))
                        // הטביעה הישנה מתחלפת רק כשההקלטה החדשה נשמרת. ביטול — נשארת הישנה.
                        TextButton(onClick = { enroll.start(row.name) }, modifier = noFocus) { Text("🎙 מחדש") }
                    } else {
                        Button(onClick = { enroll.start(row.name) }, modifier = noFocus) { Text("🎙 איתחול") }
                    }
                }
            }
        }
    }
}

/** איתחול של שחקן אחד: מקליט מהרגע שלחצו "איתחול", עד "שמור". */
@Composable
private fun Enrollment(name: String, voices: VoicesUi, micLevel: Float, enroll: EnrollActions, noFocus: Modifier) {
    // כמה זמן מקליטים — רק של התצוגה.
    var seconds by remember { mutableIntStateOf(0) }
    LaunchedEffect(voices.recording) {
        seconds = 0
        if (voices.recording) while (true) {
            delay(1_000)
            seconds++
        }
    }

    Text("איתחול קול — $name", style = MaterialTheme.typography.titleMedium)

    when {
        voices.processing -> Text("שומר את טביעת הקול…", color = Color(0xFF8AF8FF), fontSize = 18.sp)

        voices.recording -> {
            Text("🔴 מקליט — $seconds שניות. $name, הקרא בקול:", color = Color(0xFFFF6B6B), fontSize = 18.sp)
            Text(ENROLL_TEXT, fontSize = 22.sp, lineHeight = 32.sp)
            LinearProgressIndicator(progress = { micLevel }, modifier = Modifier.fillMaxWidth())
            Text("הפס צריך לזוז כשמדברים. לא זז — המיקרופון הלא נכון.", color = Color(0xFFB8BCC8), fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = enroll.save, modifier = noFocus) { Text("✓ סיימתי — שמור") }
                OutlinedButton(onClick = enroll.cancel, modifier = noFocus) { Text("ביטול") }
            }
        }

        // ניסיון שנכשל (או שהמיקרופון לא נפתח): מה קרה, ולנסות שוב.
        else -> {
            Text(ENROLL_TEXT, fontSize = 22.sp, lineHeight = 32.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = enroll.retry, modifier = noFocus) { Text("🎙 הקלט שוב") }
                OutlinedButton(onClick = enroll.cancel, modifier = noFocus) { Text("ביטול") }
            }
        }
    }
    voices.problem?.let { Text(it, color = Color(0xFFFFB86B)) }
}
