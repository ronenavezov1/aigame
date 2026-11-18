package com.aigame.repository

import com.aigame.speech.SpeechStyle
import com.aigame.speech.TextToSpeech
import kotlinx.coroutines.CancellationException
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("VoiceRepository")

// הקול של המארח: ממיר משפט לאודיו בקול ובטון שהמנצח בחר. הניגון עצמו — במסך.
class VoiceRepository(private val tts: TextToSpeech) {

    /** WAV של המשפט, או null — ואז המסך מציג רק כתובית. מסיבה לא נעצרת בגלל רשת. */
    suspend fun synthesize(text: String, style: SpeechStyle): ByteArray? = try {
        tts.synthesize(text, style)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        log.error("הקראה נכשלה, ממשיכים בכתובית: {}", e.message)
        null
    }
}
