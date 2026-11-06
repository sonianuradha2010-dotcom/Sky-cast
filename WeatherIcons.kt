package com.skycast.weather.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import com.skycast.weather.IconKind
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private val SunColor = Color(0xFFFFB300)
private val MoonColor = Color(0xFFE3E8F5)
private val RainColor = Color(0xFF4FC3F7)
private val SnowColor = Color(0xFFBBDEFB)
private val BoltColor = Color(0xFFFFD54F)

/** Hand-drawn weather glyphs, so there are no icon assets or extra dependencies. */
@Composable
fun WeatherIcon(
    kind: IconKind,
    isDay: Boolean,
    modifier: Modifier = Modifier.size(48.dp),
    animate: Boolean = false,
) {
    val dark = isSystemInDarkTheme()
    val cloudCol = if (dark) Color(0xFFB0BEC5) else Color(0xFF8FA3AD)
    val cloudBack = cloudCol.copy(alpha = 0.55f)
    val rot by rememberInfiniteTransition(label = "sun").animateFloat(
        initialValue = 0f,
        targetValue = 45f,
        animationSpec = infiniteRepeatable(tween(12_000, easing = LinearEasing), RepeatMode.Restart),
        label = "rot",
    )
    Canvas(modifier) {
        val s = min(size.width, size.height)
        val r = if (animate) rot else 0f
        when (kind) {
            IconKind.CLEAR -> if (isDay) sun(s * 0.5f, s * 0.5f, s * 0.2f, r) else moon(s * 0.5f, s * 0.5f, s * 0.28f)
            IconKind.PARTLY -> {
                if (isDay) sun(s * 0.36f, s * 0.36f, s * 0.15f, r) else moon(s * 0.36f, s * 0.36f, s * 0.2f)
                cloud(s * 0.16f, s * 0.3f, s * 0.78f, cloudCol)
            }
            IconKind.CLOUDY -> {
                cloud(s * 0.04f, s * 0.18f, s * 0.62f, cloudBack)
                cloud(s * 0.2f, s * 0.3f, s * 0.74f, cloudCol)
            }
            IconKind.FOG -> {
                cloud(s * 0.12f, s * 0.1f, s * 0.76f, cloudCol)
                for (i in 0..2) {
                    val y = s * (0.66f + i * 0.12f)
                    drawLine(cloudCol, Offset(s * (0.2f + i * 0.04f), y), Offset(s * (0.8f - i * 0.04f), y), s * 0.045f, StrokeCap.Round)
                }
            }
            IconKind.DRIZZLE -> {
                cloud(s * 0.12f, s * 0.1f, s * 0.76f, cloudCol)
                drops(s, 2, 0.08f)
            }
            IconKind.RAIN -> {
                cloud(s * 0.12f, s * 0.1f, s * 0.76f, cloudCol)
                drops(s, 3, 0.13f)
            }
            IconKind.SNOW -> {
                cloud(s * 0.12f, s * 0.1f, s * 0.76f, cloudCol)
                for (i in 0..2) drawCircle(SnowColor, s * 0.04f, Offset(s * (0.34f + i * 0.16f), s * (0.74f + (i % 2) * 0.1f)))
            }
            IconKind.STORM -> {
                cloud(s * 0.12f, s * 0.08f, s * 0.76f, cloudCol)
                val p = Path().apply {
                    moveTo(s * 0.54f, s * 0.5f)
                    lineTo(s * 0.4f, s * 0.74f)
                    lineTo(s * 0.5f, s * 0.74f)
                    lineTo(s * 0.44f, s * 0.95f)
                    lineTo(s * 0.67f, s * 0.67f)
                    lineTo(s * 0.55f, s * 0.67f)
                    lineTo(s * 0.63f, s * 0.5f)
                    close()
                }
                drawPath(p, BoltColor)
            }
        }
    }
}

private fun DrawScope.sun(cx: Float, cy: Float, r: Float, rotation: Float) {
    drawCircle(SunColor, r, Offset(cx, cy))
    for (i in 0 until 8) {
        val a = (i * 45f + rotation) * PI.toFloat() / 180f
        val p1 = Offset(cx + cos(a) * r * 1.4f, cy + sin(a) * r * 1.4f)
        val p2 = Offset(cx + cos(a) * r * 1.85f, cy + sin(a) * r * 1.85f)
        drawLine(SunColor, p1, p2, r * 0.26f, StrokeCap.Round)
    }
}

private fun DrawScope.moon(cx: Float, cy: Float, r: Float) {
    val full = Path().apply { addOval(Rect(Offset(cx, cy), r)) }
    val cut = Path().apply { addOval(Rect(Offset(cx + r * 0.55f, cy - r * 0.35f), r * 0.85f)) }
    drawPath(Path.combine(PathOperation.Difference, full, cut), MoonColor)
}

private fun DrawScope.cloud(x: Float, y: Float, w: Float, color: Color) {
    val h = w * 0.6f
    drawCircle(color, h * 0.38f, Offset(x + w * 0.28f, y + h * 0.62f))
    drawCircle(color, h * 0.5f, Offset(x + w * 0.5f, y + h * 0.45f))
    drawCircle(color, h * 0.36f, Offset(x + w * 0.74f, y + h * 0.64f))
    drawRoundRect(color, Offset(x + w * 0.18f, y + h * 0.62f), Size(w * 0.64f, h * 0.38f), CornerRadius(h * 0.19f))
}

private fun DrawScope.drops(s: Float, count: Int, len: Float) {
    for (i in 0 until count) {
        val x = s * (if (count == 3) 0.34f + i * 0.16f else 0.4f + i * 0.2f)
        val y = s * 0.68f
        drawLine(RainColor, Offset(x, y), Offset(x - s * 0.04f, y + s * len + s * 0.1f), s * 0.05f, StrokeCap.Round)
    }
}

/** Falling rain streaks / snowflakes drawn behind the hero card content. */
@Composable
fun WeatherParticles(kind: IconKind, modifier: Modifier = Modifier) {
    val falling = kind == IconKind.RAIN || kind == IconKind.DRIZZLE || kind == IconKind.STORM || kind == IconKind.SNOW
    if (!falling) return
    val snow = kind == IconKind.SNOW
    val color = LocalContentColor.current.copy(alpha = if (snow) 0.5f else 0.28f)
    val t by rememberInfiniteTransition(label = "fall").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(if (snow) 8_000 else 1_100, easing = LinearEasing), RepeatMode.Restart),
        label = "t",
    )
    val count = if (kind == IconKind.DRIZZLE) 14 else 28
    Canvas(modifier) {
        for (i in 0 until count) {
            val sx = (i * 0.61803f) % 1f
            val k = 1 + i % 2 // integer speed multiplier keeps the loop seamless
            val phase = (t * k + i * 0.113f) % 1f
            val y = phase * size.height
            if (snow) {
                val x = sx * size.width + sin((phase * 2f + i) * PI.toFloat()) * 14f
                drawCircle(color, 2.5f + (i % 3), Offset(x, y))
            } else {
                val x = sx * size.width
                drawLine(color, Offset(x, y), Offset(x - 5f, y + 22f), 2.5f, StrokeCap.Round)
            }
        }
    }
}

@Composable
fun PlayPauseIcon(playing: Boolean, modifier: Modifier = Modifier.size(22.dp), tint: Color = LocalContentColor.current) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        if (playing) {
            val bar = w * 0.26f
            drawRoundRect(tint, Offset(w * 0.18f, h * 0.12f), Size(bar, h * 0.76f), CornerRadius(bar * 0.3f))
            drawRoundRect(tint, Offset(w * 0.56f, h * 0.12f), Size(bar, h * 0.76f), CornerRadius(bar * 0.3f))
        } else {
            val p = Path().apply {
                moveTo(w * 0.25f, h * 0.12f)
                lineTo(w * 0.88f, h * 0.5f)
                lineTo(w * 0.25f, h * 0.88f)
                close()
            }
            drawPath(p, tint)
        }
    }
}
