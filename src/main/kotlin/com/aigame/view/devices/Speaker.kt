package com.aigame.view.devices

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import javax.sound.sampled.AudioSystem

// הרמקול: מנגן WAV ומדווח עוצמה תוך כדי — זה מה שמזיז את הפה.
interface Speaker {
    /** חוזר כשהצליל נגמר. ביטול עוצר את הצליל מיד. */
    suspend fun play(wav: ByteArray, onLevel: (Float) -> Unit)
}

/**
 * הבאפר קטן בכוונה (בערך 120ms). עם באפר רגיל של חצי שנייה, הפה היה זז
 * לפי מה שנכתב לבאפר ולא לפי מה שנשמע — ומקדים את הקול.
 */
class JavaSoundSpeaker : Speaker {

    override suspend fun play(wav: ByteArray, onLevel: (Float) -> Unit) = withContext(Dispatchers.IO) {
        AudioSystem.getAudioInputStream(ByteArrayInputStream(wav)).use { input ->
            val format = input.format
            // 30ms של צליל בכל חתיכה, מיושר לגודל פריים.
            val chunk = ((format.frameRate * 0.03f).toInt() * format.frameSize).coerceAtLeast(format.frameSize)
            val line = AudioSystem.getSourceDataLine(format)
            line.open(format, chunk * 4)
            line.start()
            try {
                val buf = ByteArray(chunk)
                while (currentCoroutineContext().isActive) {
                    val n = input.readNBytes(buf, 0, buf.size)
                    if (n <= 0) break
                    line.write(buf, 0, n)
                    onLevel(pcmLevel(buf, n))
                }
                if (currentCoroutineContext().isActive) line.drain()
            } finally {
                line.stop()
                line.close()
                onLevel(0f)
            }
        }
    }
}
