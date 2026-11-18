package com.aigame.storage

import com.aigame.objects.PartyClock
import com.aigame.objects.Player
import com.aigame.state.HostMemoryState
import kotlinx.serialization.builtins.ListSerializer
import org.slf4j.LoggerFactory
import java.nio.file.Path

private val log = LoggerFactory.getLogger("MemoryStorage")

// ============================================================
//  הזיכרון על הדיסק. שני קבצים ב-data/:
//
//      players.json   כל השחקנים, עם הרקע, האופי והמשימות שלהם
//      party.json     השעון: התחלה, ההתעוררות הבאה, כמה היו
//
//  הקבצים הם מצב התחלה וגיבוי. בזמן ריצה הזיכרון הוא ב-state.
//  השיחה לא נשמרת: היא רק רבע השעה האחרונה, ואחרי הפעלה מחדש
//  ממילא לא רלוונטית.
// ============================================================

class MemoryStorage(dataDir: Path) {

    private val players = FileStore(dataDir.resolve("players.json"), ListSerializer(Player.serializer()))
    private val clock = FileStore(dataDir.resolve("party.json"), PartyClock.serializer())

    fun load(): HostMemoryState {
        val memory = HostMemoryState(
            players = loadPlayers(),
            // אין שעון: המסיבה מתחילה עכשיו — ונכתב מיד, אחרת כל הדלקה תתחיל אותה מחדש.
            clock = clock.read() ?: PartyClock().also(clock::write),
        )
        log.info("הזיכרון נטען: {} שחקנים", memory.players.size)
        return memory
    }

    /** הקובץ נערך גם ביד: שחקן בלי שם לא נטען, ושם כפול — נשאר האחרון. */
    private fun loadPlayers(): Map<String, Player> {
        val all = players.read().orEmpty().map { it.copy(name = it.name.trim()) }
        val named = all.filter { it.name.isNotEmpty() }
        if (named.size < all.size) log.warn("ב-players.json יש {} שחקנים בלי שם — דולגו", all.size - named.size)
        val twice = named.groupBy { it.name }.filterValues { it.size > 1 }.keys
        if (twice.isNotEmpty()) log.warn("ב-players.json יש שמות כפולים — נשאר רק האחרון: {}", twice)
        return named.associateBy { it.name }
    }

    fun savePlayers(players: Collection<Player>) = this.players.write(players.toList())

    fun saveClock(clock: PartyClock) = this.clock.write(clock)
}
