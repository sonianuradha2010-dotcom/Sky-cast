package com.skycast.weather

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Process
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin

/**
 * Procedural ambient sound. Nothing is downloaded or bundled: rain, wind, thunder,
 * birdsong and crickets are all synthesised in real time from filtered noise and
 * oscillators, then streamed through an AudioTrack.
 */
data class Scene(
    val rain: Float = 0f,
    val wind: Float = 0f,
    val thunder: Float = 0f,
    val birds: Float = 0f,
    val crickets: Float = 0f,
) {
    val isSilent: Boolean
        get() = rain <= 0.01f && wind <= 0.01f && thunder <= 0.01f && birds <= 0.01f && crickets <= 0.01f

    companion object {
        val SILENT = Scene()
    }
}

enum class SoundMode(val label: String, val emoji: String, val scene: Scene) {
    AUTO("Auto", "✨", Scene.SILENT),
    RAIN("Rain", "🌧️", Scene(rain = 0.8f, wind = 0.12f)),
    WIND("Wind", "🍃", Scene(wind = 0.9f)),
    STORM("Storm", "⛈️", Scene(rain = 0.9f, wind = 0.5f, thunder = 1f)),
    FOREST("Forest", "🐦", Scene(birds = 1f, wind = 0.15f)),
    NIGHT("Night", "🌙", Scene(crickets = 1f, wind = 0.08f)),
}

/** Picks the soundscape that matches the live weather. */
fun sceneFor(code: Int, isDay: Boolean, windKmh: Double): Scene {
    val w = (windKmh / 45.0).coerceIn(0.08, 1.0).toFloat()
    return when (code) {
        0, 1 -> Scene(wind = 0.1f + w * 0.5f, birds = if (isDay) 0.8f else 0f, crickets = if (isDay) 0f else 0.8f)
        2 -> Scene(wind = 0.15f + w * 0.5f, birds = if (isDay) 0.5f else 0f, crickets = if (isDay) 0f else 0.5f)
        3 -> Scene(wind = 0.2f + w * 0.6f)
        45, 48 -> Scene(wind = 0.12f)
        51, 53, 55, 56, 57 -> Scene(rain = 0.3f, wind = w * 0.3f)
        61, 66, 80 -> Scene(rain = 0.55f, wind = w * 0.35f)
        63, 81 -> Scene(rain = 0.75f, wind = w * 0.4f)
        65, 67, 82 -> Scene(rain = 1f, wind = w * 0.5f)
        71, 73, 75, 77, 85, 86 -> Scene(wind = 0.1f + w * 0.4f)
        95, 96, 99 -> Scene(rain = 0.9f, wind = 0.3f + w * 0.4f, thunder = 1f)
        else -> Scene(wind = 0.2f)
    }
}

fun Scene.title(): String = when {
    thunder > 0.01f -> "Thunderstorm"
    rain > 0.8f -> "Heavy rain"
    rain > 0.5f -> "Steady rain"
    rain > 0.01f -> "Soft drizzle"
    birds > 0.01f -> "Birdsong & breeze"
    crickets > 0.01f -> "Night crickets"
    wind > 0.5f -> "Windy"
    wind > 0.01f -> "Quiet breeze"
    else -> "Silence"
}

private const val SR = 22050
private const val BLOCK = 1024
private const val TWO_PI = 6.2831855f
private const val PI_F = 3.1415927f

private fun coef(fc: Float): Float = 1f - exp(-TWO_PI * fc / SR)

private val A_RAIN_HI = coef(6500f)
private val A_RAIN_LO = coef(450f)
private val A_THUNDER = coef(70f)

class AmbientEngine {
    @Volatile var target: Scene = Scene.SILENT
    @Volatile var volume: Float = 0.6f
    @Volatile private var wantPlay = false
    private var alive = false
    private val lock = Any()

    fun start() {
        synchronized(lock) {
            wantPlay = true
            if (!alive) {
                alive = true
                Thread({ loop() }, "ambient-audio").start()
            }
        }
    }

    /** Fades out, then the audio thread shuts itself down. */
    fun stop() {
        wantPlay = false
    }

    private fun loop() {
        var track: AudioTrack? = null
        try {
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
            val minBuf = AudioTrack.getMinBufferSize(
                SR, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
            )
            track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SR)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(maxOf(minBuf, BLOCK * 4))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            track.play()

            val synth = Synth()
            val out = ShortArray(BLOCK)
            var done = false
            while (!done) {
                val wp = wantPlay
                val v = volume
                synth.render(out, target, if (wp) v * v else 0f)
                track.write(out, 0, out.size)
                if (!wp && synth.master < 0.002f) {
                    synchronized(lock) {
                        if (!wantPlay) {
                            alive = false
                            done = true
                        }
                    }
                }
            }
        } catch (e: Throwable) {
            synchronized(lock) { alive = false }
        } finally {
            try {
                track?.stop()
            } catch (e: Exception) {
                // ignore
            }
            track?.release()
        }
    }
}

private class Synth {
    private var seed = 0x2545F491

    private fun noise(): Float {
        var x = seed
        x = x xor (x shl 13)
        x = x xor (x ushr 17)
        x = x xor (x shl 5)
        seed = x
        return x / 2147483648f
    }

    private fun rnd(): Float = (noise() + 1f) * 0.5f

    var master = 0f
        private set

    // Smoothed layer levels
    private var rain = 0f
    private var wind = 0f
    private var thunder = 0f
    private var birds = 0f
    private var crickets = 0f

    // Rain
    private var rl1 = 0f
    private var rl2 = 0f
    private var dropEnv = 0f
    private var dropF = 3000f
    private var dropPh = 0f

    // Wind
    private var wl1 = 0f
    private var wl2 = 0f
    private var gp1 = 0f
    private var gp2 = 0f

    // Thunder
    private var tl1 = 0f
    private var tl2 = 0f
    private var tActive = false
    private var tT = 0f
    private var tWait = 3 * SR
    private var tGain = 1f

    // Birds
    private var bPos = 0
    private var bLen = 0
    private var bNotes = 0
    private var bGap = 0
    private var bWait = SR
    private var bBase = 3000f
    private var bF0 = 3000f
    private var bF1 = 3500f
    private var bPh = 0f

    // Crickets
    private var c1T = 0f
    private var c2T = 0.4f
    private var c1Ph = 0f
    private var c2Ph = 0f

    private fun startNote() {
        bLen = ((0.06f + 0.07f * rnd()) * SR).toInt().coerceAtLeast(100)
        bPos = 0
        bF0 = bBase * (0.9f + 0.2f * rnd())
        bF1 = (bF0 * (if (rnd() > 0.5f) 1.25f else 0.78f)).coerceIn(1800f, 5600f)
    }

    fun render(out: ShortArray, tgt: Scene, masterTarget: Float) {
        rain += (tgt.rain - rain) * 0.08f
        wind += (tgt.wind - wind) * 0.08f
        thunder += (tgt.thunder - thunder) * 0.08f
        birds += (tgt.birds - birds) * 0.08f
        crickets += (tgt.crickets - crickets) * 0.08f

        val m0 = master
        val m1 = m0 + (masterTarget - m0) * 0.12f
        master = m1

        gp1 += TWO_PI * 0.09f * BLOCK / SR
        gp2 += TWO_PI * 0.23f * BLOCK / SR
        if (gp1 > TWO_PI) gp1 -= TWO_PI
        if (gp2 > TWO_PI) gp2 -= TWO_PI
        val gustBlock = 0.6f + 0.3f * sin(gp1) + 0.1f * sin(gp2)
        val aWind = coef(130f + 480f * gustBlock * wind)

        for (i in out.indices) {
            val n = noise()
            var s = 0f

            // ---- Rain: band-limited noise plus random droplet pings
            if (rain > 0.003f) {
                rl1 += A_RAIN_HI * (n - rl1)
                rl2 += A_RAIN_LO * (n - rl2)
                if (dropEnv < 0.001f && rnd() < 0.0009f * rain) {
                    dropEnv = 0.4f + 0.6f * rnd()
                    dropF = 1800f + 3200f * rnd()
                }
                dropPh += TWO_PI * dropF / SR
                if (dropPh > TWO_PI) dropPh -= TWO_PI
                val drop = sin(dropPh) * dropEnv
                dropEnv *= 0.9965f
                s += ((rl1 - rl2) * 0.9f + drop * 0.15f) * rain
            }

            // ---- Wind: low-passed noise with slow gusting
            if (wind > 0.003f) {
                wl1 += aWind * (n - wl1)
                wl2 += aWind * (wl1 - wl2)
                val gust = 0.6f + 0.3f * sin(gp1 + i * 0.0001f) + 0.1f * sin(gp2)
                s += wl2 * 5f * (0.35f + gust) * wind
            }

            // ---- Thunder: crack + long low rumble, every 7-27 s
            if (thunder > 0.01f) {
                if (--tWait <= 0) {
                    tActive = true
                    tT = 0f
                    tGain = 0.6f + 0.4f * rnd()
                    tWait = ((7f + 20f * rnd()) * SR).toInt()
                }
            }
            if (tActive) {
                tT += 1f / SR
                tl1 += A_THUNDER * (n - tl1)
                tl2 += A_THUNDER * (tl1 - tl2)
                val env = (1f - exp(-tT / 0.05f)) * exp(-tT / 1.8f)
                val crack = n * 0.25f * exp(-tT / 0.05f)
                s += (tl2 * 14f * env + crack) * tGain * maxOf(thunder, 0.3f)
                if (tT > 9f) tActive = false
            }

            // ---- Birds: short chirp phrases
            if (birds > 0.01f) {
                if (bPos < bLen) {
                    val u = bPos.toFloat() / bLen
                    bPh += TWO_PI * (bF0 + (bF1 - bF0) * u) / SR
                    if (bPh > TWO_PI) bPh -= TWO_PI
                    s += sin(bPh) * sin(PI_F * u) * 0.13f * birds
                    bPos++
                    if (bPos >= bLen) {
                        bNotes--
                        bGap = ((0.04f + 0.05f * rnd()) * SR).toInt()
                    }
                } else if (bNotes > 0) {
                    if (--bGap <= 0) startNote()
                } else if (--bWait <= 0) {
                    bNotes = 2 + (rnd() * 4f).toInt()
                    bBase = 2300f + 2000f * rnd()
                    bWait = ((2f + 5f * rnd()) / birds * SR).toInt()
                    startNote()
                }
            }

            // ---- Crickets: two detuned pulse-train voices
            if (crickets > 0.01f) {
                c1T += 1f / SR
                if (c1T > 0.9f) c1T -= 0.9f
                c2T += 1f / SR
                if (c2T > 1.13f) c2T -= 1.13f
                var e1 = 0f
                if (c1T < 0.27f) {
                    val p = (c1T * 16f) % 1f
                    if (p < 0.55f) e1 = sin(PI_F * p / 0.55f)
                }
                var e2 = 0f
                if (c2T < 0.3f) {
                    val p = (c2T * 14f) % 1f
                    if (p < 0.55f) e2 = sin(PI_F * p / 0.55f)
                }
                c1Ph += TWO_PI * 4700f / SR
                if (c1Ph > TWO_PI) c1Ph -= TWO_PI
                c2Ph += TWO_PI * 4300f / SR
                if (c2Ph > TWO_PI) c2Ph -= TWO_PI
                s += (sin(c1Ph) * e1 * 0.5f + sin(c2Ph) * e2 * 0.4f) * 0.16f * crickets
            }

            val m = m0 + (m1 - m0) * (i / BLOCK.toFloat())
            val y = s * m * 2.2f
            out[i] = (y / (1f + abs(y)) * 30000f).toInt().toShort()
        }
    }
}
