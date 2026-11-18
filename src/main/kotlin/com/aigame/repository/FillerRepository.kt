package com.aigame.repository

import com.aigame.speech.SpeechStyle
import com.aigame.speech.SpeechVoice
import com.aigame.speech.TextToSpeech
import kotlinx.coroutines.CancellationException
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import kotlin.random.Random

private val log = LoggerFactory.getLogger("FillerRepository")

// מה המארח אומר מיד כשמשחררים את הכפתור, בזמן שהמוח עוד חושב.
// כל אחד מוקלט פעם אחת ונשמר — אחרי זה הוא יוצא בלי שום המתנה.
private val THINKING = listOf(
    "רגע. בית המשפט מתכנס.",
    "שנייה, אני חושב. זה קורה לי לפעמים.",
    "הממ. תן לי רגע.",
    "שנייה, אני בודק מה החוק אומר.",
    "רגע, אני מעבד. לאט, כי אני כלוא במחשב של רוני.",
)

// כשלא הובן כלום מההקלטה — גם זה צריך לצאת מיד, לא אחרי כמה שניות של הקראה.
private val MISHEARD = listOf(
    "מה? לא הבנתי.",
    "לא שמעתי כלום. עוד פעם, ויותר חזק.",
    "תדבר לתוך המיקרופון, לא לתוך הכוס.",
    "לא הבנתי מילה. ואני מבין הרבה.",
)
private val STYLE = SpeechStyle(SpeechVoice.PUCK, delivery = "dry and deadpan, thinking out loud")

// ============================================================
//  משפטים מוקלטים מראש, שיוצאים בלי המתנה:
//    משפטי המתנה  מבטלים את השקט של 10–15 שניות שבזמן שהמוח חושב
//    "לא הבנתי"   כשההקלטה לא הובנה
//
//    prepare()          בהדלקה, ברקע: מקליט את מה שעוד לא מוקלט
//    pick / misheard()  משפט אקראי עם האודיו שלו — או null אם עוד אין
// ============================================================

class FillerRepository(
    private val tts: TextToSpeech,
    private val dir: Path,
    private val random: Random = Random.Default,
) {
    /** משפט המתנה מוכן. */
    class Filler(val text: String, val audio: ByteArray)

    suspend fun prepare() {
        Files.createDirectories(dir)
        record(THINKING, "")
        record(MISHEARD, "misheard-")
    }

    /** משפט המתנה, בזמן שהמוח חושב. */
    fun pick(): Filler? = pickFrom(THINKING, "")

    /** "לא הבנתי". */
    fun misheard(): Filler? = pickFrom(MISHEARD, "misheard-")

    private suspend fun record(lines: List<String>, prefix: String) {
        lines.forEachIndexed { i, text ->
            val file = fileOf(prefix, i)
            if (Files.exists(file)) return@forEachIndexed
            try {
                tts.synthesize(text, STYLE)?.let { Files.write(file, it) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.warn("משפט המתנה לא הוקלט ({}): {}", text, e.message)
            }
        }
    }

    private fun pickFrom(lines: List<String>, prefix: String): Filler? {
        val ready = lines.indices.filter { Files.exists(fileOf(prefix, it)) }
        if (ready.isEmpty()) return null
        val i = ready.random(random)
        return Filler(lines[i], Files.readAllBytes(fileOf(prefix, i)))
    }

    private fun fileOf(prefix: String, i: Int): Path = dir.resolve("$prefix$i.wav")
}
