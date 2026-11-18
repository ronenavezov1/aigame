package com.aigame.orchestrator

import com.aigame.objects.Ability
import com.aigame.objects.HostExamples
import com.aigame.objects.Player
import com.aigame.objects.Task
import com.aigame.state.HostMemoryState

/**
 * כל מה שהסוכן מקבל מבחוץ — ואין לו שום דבר אחר. הוא רץ כמו בארגז חול:
 * בלי גישה לקבצים, ל-state או לעולם. יכולת שנוגעת בעולם — המימוש שלה כאן.
 */
interface HostOrchestratorContext {
    val llms: Llms
    val memory: HostMemoryState

    /** הדוגמאות שכתבת לו: משימות, ומשפטים וסלנג באופי שלו. */
    val examples: HostExamples

    /** שחקן חדש, או עדכון של שחקן קיים לפי שם — לזיכרון, ומיד לקובץ. */
    fun addPlayer(player: Player)

    /** משימה (גם קנס): אצל כל שחקן שקיבל אותה, ומיד לקובץ. */
    fun addTask(task: Task)

    /** פרס: יכולת מיוחדת לשחקן. */
    fun grantAbility(player: String, ability: Ability)

    /** השחקן השתמש ביכולת — היא נמחקת ממנו. false = אין לו אותה. */
    fun useAbility(player: String, ability: Ability): Boolean
}
