package com.aigame.repository

import com.aigame.objects.HostEvent
import com.aigame.state.AppStateManager
import com.aigame.state.Phase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.random.Random

private val log = LoggerFactory.getLogger("WakeUpRepository")
private val CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())

// מינימום המתנה: אם המחשב היה כבוי והמועד עבר, לא קופצים ברגע שהחלון נפתח.
private const val GRACE_MS = 10_000L

// הטווח שממנו מוגרל הזמן עד ההתעוררות הבאה.
data class WakeSchedule(val minMs: Long, val maxMs: Long)

// ============================================================
//  השעון המעורר: כל 5–10 דקות (הטווח מ-.env), בלי קשר לשיחות.
//
//  שיחה לא מאפסת את הספירה. כשהזמן מגיע ומישהו באמצע שיחה איתו —
//  מחכים שהשיחה תיגמר והוא יירדם (כמה שניות של שקט), ורק אז קם.
//  ככה הוא קם בקצב קבוע, ואף פעם לא קוטע מישהו באמצע.
//
//  המועד שמור בזיכרון (party.json): אחרי הפעלה מחדש ממשיכים ממנו.
// ============================================================

class WakeUpRepository(
    private val stateManager: AppStateManager,
    private val memory: MemoryRepository,
    private val schedule: WakeSchedule,
    private val scope: CoroutineScope,
    private val random: Random = Random.Default,
) {

    fun start() {
        scope.launch { ringOnSchedule() }
    }

    private suspend fun ringOnSchedule() {
        // המועד מההפעלה הקודמת, אם יש.
        var at = stateManager.hostMemoryState.value.clock.nextWakeAt
            ?.also { log.info("ממשיך מההפעלה הקודמת: ההתעוררות הבאה ב-{}", CLOCK.format(Instant.ofEpochMilli(it))) }
            ?: schedule()
        while (true) { // delay נעצר כשה-scope מבוטל
            delay((at - System.currentTimeMillis()).coerceAtLeast(GRACE_MS))
            // הזמן הגיע. מדברים איתו עכשיו? מחכים שיסיים ויירדם — לא קם באמצע שיחה.
            if (stateManager.hostState.value.phase != Phase.SLEEPING) log.info("הזמן הגיע — מחכה שהשיחה תיגמר")
            stateManager.hostState.first { it.phase == Phase.SLEEPING }
            log.info("הזמן הגיע — קם מעצמו")
            // הבאה נקבעת מעכשיו. שיחות לא יזיזו אותה.
            at = schedule()
            stateManager.addHostEvent(HostEvent.WakeUp)
        }
    }

    /** ההתעוררות הבאה: בעוד זמן אקראי מעכשיו, בטווח. */
    private fun schedule(): Long {
        val at = System.currentTimeMillis() + random.nextLong(schedule.minMs, schedule.maxMs + 1)
        memory.setNextWake(at)
        log.info("ההתעוררות הבאה ב-{}", CLOCK.format(Instant.ofEpochMilli(at)))
        return at
    }
}
