package com.aigame.state

import com.aigame.objects.ConversationLine
import com.aigame.objects.PartyClock
import com.aigame.objects.Player

// הזיכרון של הסוכן. מופע אחד, כאן.
//
//   players       מה שהוא יודע על כל אחד: רקע, אופי, ומשימות שקיבל — נשמר לקובץ
//   conversation  רק רבע השעה האחרונה, רק בזיכרון — מה שנאמר לפני כן כבר לא רלוונטי
//   clock         השעון של המסיבה — נשמר לקובץ
data class HostMemoryState(
    val players: Map<String, Player> = emptyMap(), // לפי שם
    val conversation: List<ConversationLine> = emptyList(),
    val clock: PartyClock = PartyClock(),
) {
    /** מי שהגיע הערב. */
    val present: List<Player> get() = players.values.filter { it.present }

    /** השחקן בשם הזה, בלי הבדל בין אותיות גדולות לקטנות. */
    fun player(name: String): Player? = players.values.firstOrNull { it.name.equals(name.trim(), ignoreCase = true) }
}
