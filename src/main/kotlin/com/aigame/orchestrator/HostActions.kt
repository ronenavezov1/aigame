package com.aigame.orchestrator

import com.aiagent.lib.agent.Action
import com.aigame.objects.Ability
import com.aigame.objects.Address
import com.aigame.objects.Player
import com.aigame.objects.Task
import com.aigame.speech.SpeechStyle
import com.aigame.speech.SpeechVoice
import com.aigame.state.Expression

// ============================================================
//  הפעולות של הסוכן. שני סוגים:
//
//    פרצוף, קול, מי דיבר   נרשמים ב-HostOutputBuilder, והרפוזיטורי
//                          מיישם אותם כשהתור נגמר.
//    שחקן, משימה, יכולת    נכתבים לזיכרון מיד, דרך המימוש שב-context —
//                          לפעולה עצמה אין גישה לקבצים או ל-state.
// ============================================================

class SetSpeakerAction(private val output: HostOutputBuilder, private val context: HostOrchestratorContext) : Action {

    override val name = "set_speaker"

    override val description =
        "מתקן את זיהוי הקול: מי באמת אמר את מה ששמעת עכשיו. " +
            "השתמש רק כשברור לך אחרת — למשל כשהוא אמר את שמו."

    override val parameters = mapOf(
        "name" to "השם של מי שדיבר — שחקן רשום.",
    )

    override suspend fun perform(agentId: String, args: Map<String, Any>): String {
        val asked = args["name"]?.toString()?.trim().orEmpty()
        val player = context.memory.player(asked)
            ?: return "אין שחקן בשם '$asked'. הרשומים: ${context.memory.players.keys.joinToString()}"
        output.speaker = player.name
        return "רשמתי שזה ${player.name}."
    }
}

class SetFaceAction(private val output: HostOutputBuilder) : Action {

    override val name = "set_face"

    override val description =
        "משנה את ההבעה של הפרצוף שלך על המסך, לזמן שאתה מדבר."

    override val parameters = mapOf(
        "expression" to "אחת מ: ${Expression.entries.joinToString(", ") { it.name }}",
    )

    override suspend fun perform(agentId: String, args: Map<String, Any>): String {
        val raw = args["expression"]?.toString().orEmpty()
        val expression = Expression.parse(raw)
            ?: return "אין הבעה כזו: '$raw'. האפשרויות: ${Expression.entries.joinToString { it.name }}"
        output.expression = expression
        return "הפרצוף יהיה $expression."
    }
}

class SetVoiceAction(private val output: HostOutputBuilder) : Action {

    override val name = "set_voice"

    override val description =
        "קובע איך תגיד את התשובה בקול: באיזה קול, ובאיזה טון."

    override val parameters = mapOf(
        "voice" to "אחד מ: " + SpeechVoice.entries.joinToString(" | ") { "${it.name} — ${it.description}" },
        "delivery" to "הוראת משחק קצרה באנגלית, למשל: cheeky and energetic / whispering like a villain / laughing.",
    )

    override suspend fun perform(agentId: String, args: Map<String, Any>): String {
        val raw = args["voice"]?.toString().orEmpty()
        val voice = SpeechVoice.parse(raw)
            ?: return "אין קול כזה: '$raw'. האפשרויות: ${SpeechVoice.entries.joinToString { it.name }}"
        output.style = SpeechStyle(voice, delivery = args["delivery"]?.toString()?.trim().orEmpty())
        return "הקול יהיה $voice: ${output.style.delivery}."
    }
}

class AddPlayerAction(private val context: HostOrchestratorContext) : Action {

    override val name = "add_player"

    override val description =
        "רושם שחקן חדש במסיבה, או מעדכן פרטים של שחקן קיים לפי השם. " +
            "השתמש כשמישהו מציג את עצמו. את הרקע והאופי במהלך הערב מעדכן מנתח השחקנים — לא אתה. " +
            "פרט שתשאיר ריק לא נמחק."

    override val parameters = mapOf(
        "name" to "השם שבו קוראים לו.",
        "background" to "מי הוא — מה שסיפר על עצמו או שסיפרו עליו. בעדכון: כל מה שידוע, לא רק החדש. ריק אם אין.",
        "character" to "איך אתה מסווג את האישיות שלו, לפי מה שראית ושמעת. בעדכון: כל מה שאתה חושב, לא רק החדש. ריק אם עוד אין לך דעה.",
        "address" to "איך פונים אליו בעברית: זכר, נקבה, או לא ידוע.",
    )

    override suspend fun perform(agentId: String, args: Map<String, Any>): String {
        val name = args["name"]?.toString()?.trim().orEmpty()
        if (name.isBlank()) return "לא נרשם — השם ריק."

        val existed = context.memory.player(name) != null
        context.addPlayer(
            Player(
                name = name,
                background = args["background"]?.toString()?.trim().orEmpty(),
                character = args["character"]?.toString()?.trim().orEmpty(),
                address = Address.parse(args["address"]?.toString().orEmpty()),
            )
        )
        return if (existed) "הפרטים של $name עודכנו." else "$name נרשם."
    }
}

class CreateTaskAction(private val context: HostOrchestratorContext) : Action {

    override val name = "create_task"

    override val description =
        "רושם משימה שנתת לשחקנים — גם קנס. השתמש בכל פעם שאתה מטיל משימה על מישהו."

    override val parameters = mapOf(
        "players" to "למי: שמות של שחקנים רשומים, מופרדים בפסיק.",
        "task" to "המשימה, במשפט אחד.",
    )

    override suspend fun perform(agentId: String, args: Map<String, Any>): String {
        val text = args["task"]?.toString()?.trim().orEmpty()
        if (text.isBlank()) return "לא נרשמה — המשימה ריקה."

        val asked = args["players"]?.toString().orEmpty().split(",").map { it.trim() }.filter { it.isNotEmpty() }
        if (asked.isEmpty()) return "לא נרשמה — לא נאמר למי."

        // השמות כמו שהם רשומים בזיכרון, כדי שהמשימה תגיע לשחקן הנכון.
        val memory = context.memory
        val found = asked.mapNotNull { memory.player(it) }
        if (found.size < asked.size) {
            val unknown = asked.filter { memory.player(it) == null }
            return "לא נרשמה — אין שחקנים בשם: ${unknown.joinToString()}. הרשומים: ${memory.players.keys.joinToString()}"
        }
        val away = found.filterNot { it.present }
        if (away.isNotEmpty()) return "לא נרשמה — ${away.joinToString { it.name }} לא הגיעו הערב."
        val names = found.map { it.name }.distinct()

        context.addTask(Task(names, text))
        return "המשימה נרשמה ל${names.joinToString(" ול")}."
    }
}

class GrantAbilityAction(private val context: HostOrchestratorContext) : Action {

    override val name = "grant_ability"

    override val description =
        "פרס: נותן לשחקן יכולת מיוחדת, שהוא ישתמש בה מתי שבא לו — פעם אחת. היכולות: ${Ability.menu}."

    override val parameters = mapOf(
        "player" to "מי זוכה — שחקן רשום.",
        "ability" to "אחת מ: ${Ability.entries.joinToString { it.hebrew }}",
    )

    override suspend fun perform(agentId: String, args: Map<String, Any>): String {
        val player = context.memory.player(args["player"]?.toString().orEmpty())
            ?: return "אין שחקן כזה. הרשומים: ${context.memory.players.keys.joinToString()}"
        val ability = Ability.parse(args["ability"]?.toString().orEmpty())
            ?: return "אין יכולת כזו. היכולות: ${Ability.entries.joinToString { it.hebrew }}"
        if (!player.present) return "${player.name} לא הגיע הערב — פרס רק למי שכאן."

        context.grantAbility(player.name, ability)
        return "${player.name} קיבל ${ability.hebrew}."
    }
}

class UseAbilityAction(private val context: HostOrchestratorContext) : Action {

    override val name = "use_ability"

    override val description =
        "שחקן משתמש ביכולת מיוחדת שיש לו — והיא נמחקת ממנו. אם אין לו אותה, זה ייכשל. " +
            "דאבל ומעביר: את מה שעובר למישהו אחר רשום גם עם create_task."

    override val parameters = mapOf(
        "player" to "מי משתמש — שחקן רשום.",
        "ability" to "אחת מ: ${Ability.entries.joinToString { it.hebrew }}",
    )

    override suspend fun perform(agentId: String, args: Map<String, Any>): String {
        val player = context.memory.player(args["player"]?.toString().orEmpty())
            ?: return "אין שחקן כזה. הרשומים: ${context.memory.players.keys.joinToString()}"
        val ability = Ability.parse(args["ability"]?.toString().orEmpty())
            ?: return "אין יכולת כזו. היכולות: ${Ability.entries.joinToString { it.hebrew }}"

        if (!context.useAbility(player.name, ability)) {
            val has = player.abilities.ifEmpty { listOf("כלום") }.joinToString()
            return "ל-${player.name} אין ${ability.hebrew}. יש לו: $has."
        }
        return "${player.name} השתמש ב${ability.hebrew}, והיא נמחקה ממנו."
    }
}
