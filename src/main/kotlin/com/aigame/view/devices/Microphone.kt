package com.aigame.view.devices

import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.sound.sampled.AudioFileFormat
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.TargetDataLine

// ============================================================
//  המיקרופון — לחוץ = מקליט, עוזבים = מקבלים WAV.
//
//  16kHz מונו 16 ביט: זה מה שמנועי זיהוי דיבור רוצים, ואין טעם
//  לשלוח פי שלושה מידע ברשת.
// ============================================================

interface Microphone {
    /**
     * מתחיל להקליט. [onLevel] נקרא בערך 20 פעם בשנייה עם עוצמה
     * 0..1, מתהליכון של המיקרופון.
     *
     * @return false אם אין מיקרופון זמין.
     */
    fun start(onLevel: (Float) -> Unit): Boolean

    /** עוצר ומחזיר WAV, או null אם ההקלטה קצרה מכדי להיות דיבור. */
    fun stop(): ByteArray?
}

private val log = LoggerFactory.getLogger("Microphone")

class JavaSoundMicrophone : Microphone {

    private val format = AudioFormat(16_000f, 16, 1, true, false)

    private var line: TargetDataLine? = null
    private var buffer: ByteArrayOutputStream? = null
    private var reader: Thread? = null

    @Synchronized
    override fun start(onLevel: (Float) -> Unit): Boolean {
        if (line != null) return true

        val opened = try {
            AudioSystem.getTargetDataLine(format).apply { open(format); start() }
        } catch (e: Exception) {
            log.warn("אין מיקרופון: {}", e.message)
            return false
        }

        val out = ByteArrayOutputStream()
        line = opened
        buffer = out
        reader = Thread({
            val chunk = ByteArray(CHUNK_BYTES)
            while (true) {
                // read חוסם עד שהחתיכה מלאה; אחרי close הוא מחזיר 0.
                val n = opened.read(chunk, 0, chunk.size)
                if (n <= 0) break
                out.write(chunk, 0, n)
                onLevel(pcmLevel(chunk, n))
            }
        }, "microphone").apply { isDaemon = true; start() }
        return true
    }

    @Synchronized
    override fun stop(): ByteArray? {
        val l = line ?: return null
        l.stop()
        l.close()
        reader?.join(500)
        line = null
        reader = null

        val pcm = buffer?.toByteArray() ?: return null
        buffer = null
        // לחיצה מהירה בטעות, לא משפט.
        if (pcm.size < MIN_BYTES) return null

        val wav = ByteArrayOutputStream()
        AudioSystem.write(
            AudioInputStream(ByteArrayInputStream(pcm), format, pcm.size / format.frameSize.toLong()),
            AudioFileFormat.Type.WAVE,
            wav,
        )
        return wav.toByteArray()
    }

    private companion object {
        /** 50ms: 16000 דגימות × 2 בתים × 0.05. */
        const val CHUNK_BYTES = 1600

        /** פחות מ-0.3 שניות. */
        const val MIN_BYTES = 9600
    }
}
