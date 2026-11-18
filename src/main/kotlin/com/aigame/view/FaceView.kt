package com.aigame.view

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.aigame.state.Expression
import com.aigame.viewmodel.FaceMode
import com.aigame.viewmodel.FaceUi
import kotlinx.coroutines.delay
import kotlin.math.min
import kotlin.random.Random

// ============================================================
//  הפרצוף. שתי עיניים ופה שזוהרים על רקע כהה — בלי ראש, בלי
//  אף. פחות פרטים = יותר אופי, והוא נקרא מכל פינה בחדר.
//
//  כל מה שמשתנה מגיע מ-FaceUi. העוצמות מגיעות מהמכשירים של המסך:
//  micLevel מהמיקרופון (הטבעת), mouthOpen מהרמקול (הפה). מה שחי רק
//  כאן הוא תנועה טהורה: מצמוץ, נשימה בשינה, נקודות החשיבה.
// ============================================================

val FaceBackground = Color(0xFF0B0B12)

@Composable
fun FaceView(face: FaceUi, micLevel: Float, mouthOpen: Float, modifier: Modifier = Modifier) {
    val color by animateColorAsState(colorFor(face), tween(400))
    val mouth by animateFloatAsState(mouthOpen, tween(70))
    val mic by animateFloatAsState(micLevel, tween(90))
    val awake by animateFloatAsState(if (face.mode == FaceMode.SLEEPING) 0f else 1f, tween(450))

    val blink = remember { Animatable(0f) }
    LaunchedEffect(face.mode) {
        if (face.mode == FaceMode.SLEEPING) return@LaunchedEffect
        while (true) {
            delay(Random.nextLong(2_500, 6_000))
            blink.animateTo(1f, tween(70))
            blink.animateTo(0f, tween(110))
        }
    }

    val loop = rememberInfiniteTransition()
    val breathe by loop.animateFloat(0f, 1f, infiniteRepeatable(tween(2_600), RepeatMode.Reverse))
    val spin by loop.animateFloat(0f, 1f, infiniteRepeatable(tween(900, easing = LinearEasing)))

    val textMeasurer = rememberTextMeasurer()

    Canvas(modifier) {
        val u = min(size.width, size.height) / 10f
        val center = Offset(size.width / 2, size.height * 0.46f)

        val look = if (face.mode == FaceMode.THINKING) Offset(u * 0.5f, -u * 0.6f) else Offset.Zero
        val openness = awake * (1f - blink.value)

        for (side in listOf(-1f, 1f)) {
            val eyeCenter = Offset(center.x + side * u * 2.4f, center.y - u * 0.6f) + look
            drawEye(eyeCenter, side, u, face, openness, mic, breathe, color)
        }

        drawMouth(Offset(center.x, center.y + u * 2.1f), u, face, mouth, color)

        when (face.mode) {
            FaceMode.LISTENING -> drawCircle(
                color = color.copy(alpha = 0.2f + 0.6f * mic),
                radius = u * 3.9f * (1f + 0.08f * mic),
                center = center + Offset(0f, u * 0.4f),
                style = Stroke(u * 0.12f),
            )

            FaceMode.THINKING -> for (i in 0..2) {
                val phase = (spin * 3 - i).let { if (it < 0) it + 3 else it }
                val alpha = if (phase < 1f) 1f - phase else 0.15f
                drawCircle(color.copy(alpha = alpha), u * 0.22f, center + Offset(u * (3.4f + i * 0.7f), -u * 3.2f))
            }

            FaceMode.SLEEPING -> for (i in 0..2) {
                drawText(
                    textMeasurer = textMeasurer,
                    text = "z",
                    topLeft = center + Offset(u * (3.2f + i * 0.8f), -u * (2.6f + i * 0.9f + breathe * 0.3f)),
                    style = TextStyle(
                        color = color.copy(alpha = 0.25f + 0.25f * i),
                        fontSize = (u * (0.5f + 0.25f * i)).toSp(),
                        fontWeight = FontWeight.Bold,
                    ),
                )
            }

            else -> Unit
        }
    }
}

private fun DrawScope.drawEye(
    c: Offset,
    side: Float,
    u: Float,
    face: FaceUi,
    openness: Float,
    mic: Float,
    breathe: Float,
    color: Color,
) {
    val w = u * 1.7f
    val stroke = Stroke(u * 0.32f, cap = StrokeCap.Round)

    // עין עצומה — קו. בשינה הוא עולה ויורד עם הנשימה.
    if (openness < 0.12f) {
        val y = c.y + u * 0.4f + breathe * u * 0.12f
        drawLine(color, Offset(c.x - w / 2, y), Offset(c.x + w / 2, y), u * 0.28f, StrokeCap.Round)
        return
    }

    val happy = face.expression == Expression.HAPPY || face.expression == Expression.LAUGHING
    if (happy && face.mode != FaceMode.LISTENING) {
        // ^ ^ — קשת הפוכה.
        drawArc(color, 200f, 140f, false, Offset(c.x - w / 2, c.y - u * 0.4f), Size(w, u * 1.6f), style = stroke)
        return
    }

    val scale = when {
        face.mode == FaceMode.LISTENING -> 1.12f + mic * 0.18f
        face.expression == Expression.SURPRISED -> 1.25f
        else -> 1f
    }
    val squint = when (face.expression) {
        Expression.SUSPICIOUS -> if (side > 0) 0.35f else 0.9f
        Expression.BORED -> 0.45f
        Expression.EVIL -> 0.75f
        else -> 1f
    }
    val h = u * 2.3f * scale * openness * squint
    val ew = w * scale
    drawRoundRect(color, Offset(c.x - ew / 2, c.y - h / 2), Size(ew, h), CornerRadius(ew * 0.45f))

    // גבה כועסת: משולש בצבע הרקע חותך את הפינה הפנימית העליונה.
    if (face.expression == Expression.EVIL) {
        val inner = c.x - side * ew / 2
        val outer = c.x + side * ew / 2
        val top = c.y - h / 2 - u * 0.1f
        drawPath(
            Path().apply {
                moveTo(inner - side * u * 0.2f, top)
                lineTo(outer + side * u * 0.2f, top)
                lineTo(inner - side * u * 0.2f, top + h * 0.6f)
                close()
            },
            FaceBackground,
        )
    }
}

private fun DrawScope.drawMouth(c: Offset, u: Float, face: FaceUi, open: Float, color: Color) {
    val w = u * 2.6f
    val stroke = Stroke(u * 0.3f, cap = StrokeCap.Round)

    if (face.mode == FaceMode.SPEAKING) {
        val h = u * (0.25f + 1.5f * open)
        val mw = w * (0.8f + 0.2f * (1f - open))
        drawRoundRect(color, Offset(c.x - mw / 2, c.y - h / 2), Size(mw, h), CornerRadius(min(mw, h) / 2))
        return
    }

    when (face.expression) {
        Expression.HAPPY ->
            drawArc(color, 20f, 140f, false, Offset(c.x - w / 2, c.y - u * 1.1f), Size(w, u * 1.6f), style = stroke)

        Expression.LAUGHING ->
            drawArc(color, 0f, 180f, true, Offset(c.x - w / 2, c.y - u * 0.8f), Size(w, u * 1.8f))

        Expression.EVIL -> drawPath(
            Path().apply {
                moveTo(c.x - w / 2, c.y - u * 0.1f)
                quadraticTo(c.x, c.y + u * 0.6f, c.x + w / 2, c.y - u * 0.5f)
            },
            color,
            style = stroke,
        )

        Expression.SURPRISED -> drawCircle(color, u * 0.5f, c, style = stroke)

        Expression.BORED ->
            drawLine(color, Offset(c.x - w * 0.3f, c.y + u * 0.2f), Offset(c.x + w * 0.3f, c.y + u * 0.2f), u * 0.28f, StrokeCap.Round)

        Expression.NEUTRAL, Expression.SUSPICIOUS -> {
            val tilt = if (face.expression == Expression.SUSPICIOUS) u * 0.25f else 0f
            drawLine(color, Offset(c.x - w * 0.4f, c.y + tilt), Offset(c.x + w * 0.4f, c.y - tilt), u * 0.28f, StrokeCap.Round)
        }
    }
}

private fun colorFor(face: FaceUi): Color = when {
    face.mode == FaceMode.SLEEPING -> Color(0xFF2E6A73)
    face.mode == FaceMode.LISTENING -> Color(0xFF8AF8FF)
    else -> when (face.expression) {
        Expression.NEUTRAL -> Color(0xFF5CE1E6)
        Expression.HAPPY, Expression.LAUGHING -> Color(0xFFFFD166)
        Expression.EVIL -> Color(0xFFFF4D6D)
        Expression.SUSPICIOUS -> Color(0xFFB794F6)
        Expression.SURPRISED -> Color(0xFFE6FAFF)
        Expression.BORED -> Color(0xFF8A94A6)
    }
}
