package com.tbzmike.vplus

import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import kotlin.math.roundToInt

/**
 * Best-effort system output enhancer.
 *
 * Session 0 asks Android for the global output effect where the device audio
 * implementation permits it. Android/OEM policy can deny global effects; in
 * that case the engine reports unavailable instead of pretending it worked.
 */
class AudioBoostEngine {
    private var loudness: LoudnessEnhancer? = null
    private var equalizer: Equalizer? = null

    var available: Boolean = false
        private set

    var enabled: Boolean = false
        private set

    fun initialize(): Boolean {
        release()
        return try {
            val le = LoudnessEnhancer(0)
            val eq = Equalizer(0, 0)
            if (!le.hasControl() && !eq.hasControl()) {
                le.release()
                eq.release()
                available = false
                return false
            }
            loudness = le
            equalizer = eq
            available = true
            setBoostPercent(100)
            setVoiceClarity(true)
            true
        } catch (_: Throwable) {
            release()
            false
        }
    }

    fun setEnabled(value: Boolean) {
        enabled = value
        loudness?.enabled = value
        equalizer?.enabled = value
    }

    /**
     * 100% is unity. 200% is the maximum user setting and maps to +6 dB,
     * while LoudnessEnhancer provides its own out-of-range compression.
     */
    fun setBoostPercent(percent: Int) {
        val p = percent.coerceIn(100, 200)
        val gainDb = ((p - 100) / 100f) * 6f
        loudness?.setTargetGain((gainDb * 1000f).roundToInt())
    }

    /** Mild speech-focused EQ. Boosts are intentionally limited to reduce clipping. */
    fun setVoiceClarity(enabled: Boolean) {
        val eq = equalizer ?: return
        if (!enabled) {
            for (band in 0 until eq.numberOfBands) eq.setBandLevel(band.toShort(), 0)
            return
        }

        val boostsDb = mapOf(
            250 to -2f,
            500 to -1f,
            1000 to 1f,
            2000 to 2f,
            4000 to 3f,
            8000 to 2f,
            16000 to 1f
        )

        for (band in 0 until eq.numberOfBands) {
            val range = eq.getBandFreqRange(band.toShort())
            val centerHz = (range[0] + range[1]) / 2
            val target = boostsDb.entries.minByOrNull { kotlin.math.abs(it.key - centerHz) }?.value ?: 0f
            val millibels = (target * 100).roundToInt()
            val min = eq.bandLevelRange[0].toInt()
            val max = eq.bandLevelRange[1].toInt()
            eq.setBandLevel(band.toShort(), millibels.coerceIn(min, max).toShort())
        }
    }

    fun release() {
        enabled = false
        available = false
        try { loudness?.release() } catch (_: Throwable) {}
        try { equalizer?.release() } catch (_: Throwable) {}
        loudness = null
        equalizer = null
    }
}
