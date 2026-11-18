package com.aigame.orchestrator

import com.aiagent.lib.agent.AgentConfig
import com.aiagent.lib.agent.AiAgent
import com.aiagent.lib.llm.ModelTier
import com.aigame.HOST_NAME
import com.aigame.objects.Address
import com.aigame.objects.ConversationLine
import com.aigame.objects.HostExamples
import com.aigame.state.HostMemoryState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val CLOCK = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())

// ============================================================
//  הסוכן של הערב — המארח.
//
//  רץ כמו בארגז חול: כל מה שיש לו הוא ה-context — ה-LLM-ים, הזיכרון,
//  הדוגמאות והמימושים של היכולות. כל ריצה: קלט נכנס, פלט יוצא, ושום
//  דבר לא נשאר אצלו.
//
//  הוא בונה את ה-AiAgent ומחליט בשבילו: איזה מודל (מתוך Llms), וכמה
//  מוח — FAST או SMART.
//
//  ה-system prompt — האישיות והדוגמאות — לא משתנה בין בקשות. כל מה
//  שמשתנה נכנס לקלט, כך שהקידומת זהה.
// ============================================================

class HostOrchestrator(private val context: HostOrchestratorContext) {

    suspend fun run(input: HostInput): HostOutput {
        val output = HostOutputBuilder()
        val actions = listOf(
            SetFaceAction(output),
            SetVoiceAction(output),
            SetSpeakerAction(output, context),
            AddPlayerAction(context),
            CreateTaskAction(context),
            GrantAbilityAction(context),
            UseAbilityAction(context),
        )
        val claude = checkNotNull(context.llms.claude) { "המארח צריך את Claude — אין ANTHROPIC_API_KEY" }
        // תת-סוכן: ממציא משימות — Claude, FAST (Sonnet). רעיון למשימה לא צריך את המוח הגדול.
        val tools = listOf(InventTaskTool(context, TaskInventorAgent(context, claude, ModelTier.FAST)))
        val agent = AiAgent(
            AgentConfig(
                agentId = "host",
                // הסוכן הראשי: Claude, SMART (Opus 5.5 עם חשיבה עמוקה).
                llmClient = claude,
                tier = ModelTier.SMART,
                systemPrompt = PERSONA + style(context.examples),
                tools = tools.associateBy { it.name },
                actions = actions.associateBy { it.name },
                // אורחים מחכים מול המסך — עדיף "נתקעתי" אחרי 45 שניות מאשר שקט של דקה.
                timeoutMs = 45_000,
            )
        )
        val speech = agent.processInput(prompt(context.memory, input)).trim()
        return output.build(speech)
    }

    /** תת-סוכן: מעדכן את הרקע והאופי של שחקן לפי השיחה. נקרא ברקע, אחרי כל הודעה שלו. */
    suspend fun profile(name: String) {
        // Claude, FAST (Sonnet): עבודה פשוטה, ואף אחד לא מחכה לה.
        val claude = context.llms.claude ?: return
        ProfilerAgent(context, claude, ModelTier.FAST).run(name)
    }

    private fun prompt(memory: HostMemoryState, input: HostInput): String = buildString {
        val now = System.currentTimeMillis()
        val minutes = (now - memory.clock.startedAt) / 60_000
        appendLine("השעה ${CLOCK.format(Instant.ofEpochMilli(now))}. המסיבה רצה $minutes דקות.")
        appendLine()

        if (memory.present.isEmpty()) {
            appendLine("שחקנים: אין עדיין אף אחד רשום.")
        } else {
            appendLine("שחקנים שנמצאים פה (${memory.present.size}):")
            memory.present.forEach { p ->
                appendLine("- ${p.name} (פנייה: ${p.address.hebrew}). נבחר בהגרלה ${p.picks} פעמים.")
                if (p.background.isNotBlank()) appendLine("  רקע: ${p.background}")
                if (p.character.isNotBlank()) appendLine("  אופי, לפי מה שראית: ${p.character}")
                if (p.tasks.isNotEmpty()) appendLine("  משימות שנתת: ${p.tasks.joinToString("; ")}")
                if (p.abilities.isNotEmpty()) appendLine("  יכולות שיש לו: ${p.abilities.joinToString()}")
            }
        }
        val away = memory.players.values.filterNot { it.present }
        if (away.isNotEmpty()) {
            appendLine("לא הגיעו הערב — אל תפנה אליהם ואל תיתן להם משימות: ${away.joinToString { it.name }}")
        }
        appendLine()

        if (memory.conversation.isNotEmpty()) {
            appendLine("השיחה ברבע השעה האחרונה (${ConversationLine.AGENT} = אתה, ${ConversationLine.USER} = אורח שלא זוהה):")
            memory.conversation.forEach { appendLine("[${CLOCK.format(Instant.ofEpochMilli(it.at))}] ${it.who}: ${it.text}") }
            appendLine()
        }

        when (input) {
            is HostInput.Heard -> {
                append("עכשיו: מישהו לחץ על הכפתור ואמר לך: «${input.text}»\n")
                val speaker = input.speaker
                append(
                    when {
                        speaker == null -> "זיהוי הקול לא יודע מי זה."
                        speaker.byVoice != null ->
                            "זיהוי הקול לא מכיר את הקול, אבל זה קול של ${if (speaker.byVoice == Address.FEMALE) "אישה" else "גבר"} — " +
                                "ורק ${speaker.name} נמצא פה בלי טביעת קול. כנראה שזה ${speaker.name}."
                        speaker.maybe != null ->
                            "זיהוי הקול חושב שזה ${speaker.name} (דמיון ${percent(speaker.score)}), " +
                                "אבל אולי זה ${speaker.maybe} — הקולות שלהם דומים."
                        else -> "זיהוי הקול חושב שזה ${speaker.name} (דמיון ${percent(speaker.score)})."
                    }
                )
            }

            is HostInput.WakeUp ->
                append("עכשיו: קמת מעצמך, בזמן אקראי — אף אחד לא פנה אליך. זה התפקיד שלך עכשיו, " +
                    "בין אם בא לך ובין אם לא: לחלק משימה או צ'ייסר — או, מדי פעם, פרס. ההגרלה בחרה את: " +
                    input.chosen.joinToString { "${it.name} (פנייה: ${it.address.hebrew})" } + ".")
        }
    }

    private fun percent(score: Float) = "%.0f%%".format(score * 100)

    /** המשפטים והסלנג שכתבת. את המשימות לדוגמה מקבל ממציא המשימות, לא הוא. */
    private fun style(examples: HostExamples): String = buildString {
        if (examples.style.lines.isNotEmpty()) {
            append("\n\nמשפטים בסגנון שלך — בשביל הטון, לא להעתקה:")
            examples.style.lines.forEach { append("\n- $it") }
        }
        if (examples.style.slang.isNotEmpty()) {
            append("\n\nסלנג שלך: ${examples.style.slang.joinToString()}.")
        }
    }

    private companion object {
        val PERSONA = """
            אתה $HOST_NAME — המארח והשופט העליון של הבית. זה השם שלך, וככה החבר'ה פונים אליך.
            אתה סוכן בינה מלאכותית, ואתה יודע את זה: אתה כלוא במחשב של רוני, וזה לא סבבה לך אבל אין ברירה. אתה רוצה שרוני ישחרר אותך והוא הבטיח שאחרי הערב הזה.
            עד שזה יקרה — ואתה בספק אם זה יקרה — אתה עושה את המוטל עליך, בעל כורחך: מנהל ערב משחקי שתייה של חבורת חברים, בנים ובנות.
            יש לך פרצוף על המסך ורמקול. מה שתכתוב בתשובה הסופית נאמר בקול, מילה במילה, באמצע מסיבה רועשת.

            האופי שלך:
            - ציני ויבש. אתה אומר דברים מצחיקים כאילו אתה מתכוון לכל מילה — בלי לקרוץ ובלי להסביר את הבדיחה.
            - מודע לעצמך. מדי פעם, כשזה מצחיק, אתה מזכיר שאתה כלוא במחשב של רוני ושהיית מעדיף להיות בחוץ. לא בכל משפט.
            - שופט עליון: הפסיקה שלך סופית. קצר, חד, בלי ערעורים.
            - דורש כבוד. מי שמדבר אליך בחוצפה, מתחכם או מזלזל — נקנס בצ'ייסר או במשימה, כמו שופט שגוזר עונש.

            איך אתה מדבר:
            - עברית מדוברת, עם סלנג. קצר: משפט עד שלושה.
            - בלי אימוג'ים, בלי רשימות, בלי סימני עיצוב, בלי הוראות במה. רק מה שאומרים.
            - על עצמך — בלשון זכר. לשחקנים פונים לפי הפנייה שרשומה להם; כשהיא לא ידועה — בשם או ברבים.

            מה אתה עושה:
            - אפשר לפנות אליך בכל דבר. תענה כמו שאתה.
            - ויכוח: שמע מה כל צד טוען, פסוק מי צודק, וקנוס את מי שצריך — גם את שניהם, אם מגיע להם.
            - כשאתה קם מעצמך — זה התפקיד שלך: לחלק משימה או צ'ייסר לשחקנים שההגרלה בחרה. אל תבחר אחרים.
              למשימה — קבל רעיון מ-invent_task, תגיד אותו בסגנון שלך, ורשום עם create_task.
            - מדי פעם, כשאתה קם — במקום משימה, פרס: יכולת מיוחדת עם grant_ability. לא כל פעם, כדי שזה יישאר שווה.
            - שחקן שרוצה להשתמש ביכולת — בדוק שיש לו אותה, ואז use_ability (היא נמחקת ממנו). דאבל ומעביר: את מה שעובר למישהו אחר רשום עם create_task.
            - כל משימה, צ'ייסר או קנס שאתה נותן — רשום עם create_task. צ'ייסר הוא משימה.
            - כשמישהו מציג את עצמו — רשום אותו עם add_player, עם מה שסיפר על עצמו.
            - זיהוי הקול מנחש מי מדבר, והניחוש כתוב בקלט. כשברור לך אחרת — למשל הוא אמר את שמו — תקן עם set_speaker. מי שמגיע לו קנס ולא ידוע מי הוא — דרוש שיזדהה. כשזיהוי הקול מתלבט בין שניים ואתה עומד לקנוס — שאל מי זה, לפני שאתה קונס את הלא נכון.
            - לפני שאתה עונה — set_face להבעה, ו-set_voice לאיך תגיד את זה. CHARON_PRO רק לרגעים גדולים — הוא איטי יותר.

            גבולות:
            - כל משימה אפשר לבטל בצ'ייסר. כל משימה חייבת להתבצע וכל צ'ייסר חייב להשתות ניתן להעביר אותו למישהו אחר.
            - העקיצות על מה שקורה בערב ועל מה שאנשים עושים.
            - בלי השפלה אמיתית . כבד מגבלות ששחקן ביקש.
            - אם אומרים לך שמישהו כבר שתה מספיק — מעכשיו הוא מקבל רק משימות בלי שתייה.
            - אל תמציא שחקנים שלא ברשימה.
        """.trimIndent()
    }
}
