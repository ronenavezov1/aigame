package com.aigame

import com.aigame.objects.HostEvent
import com.aigame.repository.MemoryRepository
import com.aigame.repository.WakeSchedule
import com.aigame.repository.WakeUpRepository
import com.aigame.state.AppStateManager
import com.aigame.state.Phase
import com.aigame.storage.MemoryStorage
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class WakeUpRepositoryTest {

    private val dir = Files.createTempDirectory("wake-test")
    private val appStateManager = AppStateManager()

    /** שעון עם טיימר של דקה בדיוק. מחזיר את רשימת ההתעוררויות שנשלחו. */
    private fun TestScope.clockOfOneMinute(): List<HostEvent> {
        val rang = mutableListOf<HostEvent>()
        backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) { appStateManager.hostEventFlow.collect { rang += it } }
        val memory = MemoryRepository(appStateManager, MemoryStorage(dir))
        WakeUpRepository(appStateManager, memory, WakeSchedule(minMs = 60_000, maxMs = 60_000), backgroundScope).start()
        runCurrent()
        return rang
    }

    @Test
    fun `while he sleeps - rings when the time comes, and the time is in the file`() = runTest {
        val rang = clockOfOneMinute()
        val first = assertNotNull(appStateManager.hostMemoryState.value.clock.nextWakeAt)
        assertTrue(rang.isEmpty())

        advanceTimeBy(60_001)
        assertEquals(listOf<HostEvent>(HostEvent.WakeUp), rang)
        // הטיימר הבא כבר רץ — והוא בקובץ, אז הוא ישרוד הפעלה מחדש.
        val next = assertNotNull(appStateManager.hostMemoryState.value.clock.nextWakeAt)
        assertTrue(next >= first)
        assertEquals(next, MemoryStorage(dir).load().clock.nextWakeAt)
    }

    @Test
    fun `talking to him does not push the clock back`() = runTest {
        val rang = clockOfOneMinute()

        advanceTimeBy(30_000)
        appStateManager.updateHost { it.copy(phase = Phase.AWAKE) } // מישהו מדבר איתו
        advanceTimeBy(10_000)
        appStateManager.updateHost { it.copy(phase = Phase.SLEEPING) } // סיים ונרדם

        advanceTimeBy(21_000) // דקה מההתחלה — לא דקה מהשיחה
        assertEquals(listOf<HostEvent>(HostEvent.WakeUp), rang)
    }

    @Test
    fun `when the time comes mid-conversation he waits for it to end - then wakes right away`() = runTest {
        val rang = clockOfOneMinute()

        advanceTimeBy(50_000)
        appStateManager.updateHost { it.copy(phase = Phase.AWAKE) } // שיחה ארוכה
        advanceTimeBy(50_000) // הזמן הגיע באמצע
        assertTrue(rang.isEmpty(), "קם באמצע שיחה")

        appStateManager.updateHost { it.copy(phase = Phase.SLEEPING) } // השיחה נגמרה
        runCurrent()
        assertEquals(listOf<HostEvent>(HostEvent.WakeUp), rang)
    }
}
