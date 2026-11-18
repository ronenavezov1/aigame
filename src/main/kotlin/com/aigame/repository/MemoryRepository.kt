package com.aigame.repository

import com.aigame.objects.Ability
import com.aigame.objects.Address
import com.aigame.objects.ConversationLine
import com.aigame.objects.Player
import com.aigame.objects.Task
import com.aigame.state.AppStateManager
import com.aigame.storage.MemoryStorage

// כמה זמן משפט נשאר בשיחה. מה שנאמר לפני כן כבר לא רלוונטי — מה שחשוב
// ממנו נשאר אצל השחקנים, ברקע, באופי ובמשימות.
private const val CONVERSATION_WINDOW_MS = 15 * 60_000L

// ============================================================
//  הזיכרון: כל שינוי בו עובר כאן.
//
//    שחקנים ושעון   ל-state, ומיד לקובץ — כיבוי לא מאבד כלום.
//    שיחה           רק ב-state, רק רבע השעה האחרונה.
//
//  לקבצים נכתב תמיד הערך העדכני מה-state — לא עותק שנלקח לפני
//  השינוי — כך ששני שינויים קרובים לא דורסים זה את זה.
// ============================================================

class MemoryRepository(
    private val stateManager: AppStateManager,
    private val storage: MemoryStorage,
    private val now: () -> Long = System::currentTimeMillis,
) {
    /** בהדלקה: מה שבקבצים נכנס ל-state. */
    fun load() = stateManager.updateHostMemory { storage.load() }

    /**
     * שחקן חדש, או עדכון של קיים לפי שם. פרט ריק לא מוחק את מה שכבר ידוע.
     * מי שנרשם או מעודכן — מדבר עכשיו, אז הוא כאן: מסומן נוכח.
     */
    fun addPlayer(player: Player) {
        stateManager.updateHostMemory { memory ->
            val old = memory.player(player.name)
            val merged = old?.copy(
                background = player.background.ifBlank { old.background },
                character = player.character.ifBlank { old.character },
                address = if (player.address == Address.UNKNOWN) old.address else player.address,
                present = true,
            ) ?: player
            memory.copy(players = memory.players + (merged.name to merged))
        }
        savePlayers()
    }

    /** משימה (גם קנס): אצל כל שחקן שקיבל אותה. */
    fun addTask(task: Task) {
        stateManager.updateHostMemory { memory ->
            memory.copy(players = memory.players.mapValues { (name, player) ->
                if (name in task.players) player.copy(tasks = player.tasks + task.text) else player
            })
        }
        savePlayers()
    }

    /** סימון בהגדרות: הגיע הערב, או לא. */
    fun setPresent(player: String, present: Boolean) {
        updatePlayer(player) { it.copy(present = present) }
    }

    /** פרס: עוד יכולת לשחקן. */
    fun grantAbility(player: String, ability: Ability) {
        updatePlayer(player) { it.copy(abilities = it.abilities + ability.hebrew) }
    }

    /** השחקן השתמש ביכולת — אחת מהן נמחקת. false = אין לו אותה. */
    fun useAbility(player: String, ability: Ability): Boolean {
        val has = stateManager.hostMemoryState.value.player(player)?.abilities?.contains(ability.hebrew) == true
        if (has) updatePlayer(player) { it.copy(abilities = it.abilities - ability.hebrew) }
        return has
    }

    /** הגיעה הודעה חדשה: מה שנאמר לפני יותר מרבע שעה — לפח. */
    fun forgetOldConversation() {
        val since = now() - CONVERSATION_WINDOW_MS
        stateManager.updateHostMemory { memory -> memory.copy(conversation = memory.conversation.filter { it.at >= since }) }
    }

    /** תור אחד בשיחה: מה נאמר לסוכן, ומה הוא ענה. */
    fun addConversation(input: ConversationLine, answer: ConversationLine) {
        stateManager.updateHostMemory { it.copy(conversation = it.conversation + input + answer) }
    }

    /** ההגרלה בחרה אותם — עוד פעם אחת לכל אחד. */
    fun markPicked(names: Collection<String>) {
        stateManager.updateHostMemory { memory ->
            memory.copy(players = memory.players.mapValues { (name, player) ->
                if (name in names) player.copy(picks = player.picks + 1) else player
            })
        }
        savePlayers()
    }

    fun countWakeUp() {
        stateManager.updateHostMemory { it.copy(clock = it.clock.copy(wakeCount = it.clock.wakeCount + 1)) }
        storage.saveClock(stateManager.hostMemoryState.value.clock)
    }

    fun setNextWake(at: Long) {
        stateManager.updateHostMemory { it.copy(clock = it.clock.copy(nextWakeAt = at)) }
        storage.saveClock(stateManager.hostMemoryState.value.clock)
    }

    /** שינוי בשחקן אחד, לפי שם — ומיד לקובץ. */
    private fun updatePlayer(name: String, change: (Player) -> Player) {
        stateManager.updateHostMemory { memory ->
            val player = memory.player(name) ?: return@updateHostMemory memory
            memory.copy(players = memory.players + (player.name to change(player)))
        }
        savePlayers()
    }

    private fun savePlayers() = storage.savePlayers(stateManager.hostMemoryState.value.players.values)
}
