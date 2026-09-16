package com.tbzmike.vplus

import android.content.Context
import android.os.Build
import java.io.BufferedReader
import java.io.InputStreamReader
import rikka.shizuku.Shizuku

/**
 * Privileged audio layer. Root can execute the bundled TinyALSA helper from
 * nativeLibraryDir, while Shizuku remains available for platform stream volume.
 * Hardware controls are discovered at runtime; no device-specific mixer names
 * or ranges are hard-coded.
 */
class PrivilegedAudioBackend(private val context: Context) {
    enum class Mode { NONE, ROOT, SHIZUKU }

    var mode: Mode = Mode.NONE
        private set

    var lastHardwareStatus: String = "Hardware mixer not scanned"
        private set

    private val snapshotFile: String
        get() = "${context.filesDir.absolutePath}/vplus-mixer.snapshot"

    fun detect(): Mode {
        if (hasRoot()) {
            mode = Mode.ROOT
            return mode
        }
        mode = if (isShizukuAvailable()) Mode.SHIZUKU else Mode.NONE
        return mode
    }

    fun isAvailable(): Boolean = detect() != Mode.NONE

    /** Discover the real RX mixer controls without changing anything. */
    fun scanHardwareMixer(): Pair<Boolean, String> {
        if (detect() != Mode.ROOT) return false to "Root is required for direct ALSA mixer access"
        val result = runRoot("$toolPath scan")
        lastHardwareStatus = result.second
        return result
    }

    /** Capture the original controls once, then apply the requested boost. */
    fun maximizeCallVolume(percent: Int = 200): Pair<Boolean, String> {
        val p = percent.coerceIn(100, 200)
        val voice = runPrivileged("cmd media_session volume --stream 0 --set 100")
        val media = runPrivileged("cmd media_session volume --stream 3 --set 100")

        if (detect() != Mode.ROOT) {
            return (voice.first || media.first) to listOf(voice.second, media.second).filter { it.isNotBlank() }.joinToString("\n")
        }

        val snapshot = runRoot("$toolPath snapshot > ${shellQuote(snapshotFile)}")
        if (!snapshot.first) return false to "Hardware snapshot failed: ${snapshot.second}"

        val hardware = setHardwareBoost(p, fromSnapshot = true)
        return hardware.first to listOf(voice.second, media.second, hardware.second).filter { it.isNotBlank() }.joinToString("\n")
    }

    /** Restore to the saved baseline before applying a new hardware target. */
    fun setHardwareBoost(percent: Int): Pair<Boolean, String> = setHardwareBoost(percent, fromSnapshot = false)

    private fun setHardwareBoost(percent: Int, fromSnapshot: Boolean): Pair<Boolean, String> {
        if (detect() != Mode.ROOT) return false to "Root is required for direct ALSA mixer access"
        if (!fromSnapshot) {
            val restored = runRoot("$toolPath restore ${shellQuote(snapshotFile)}")
            if (!restored.first) return restored
        }
        val p = percent.coerceIn(100, 200)
        val result = if (p == 100) {
            true to "Hardware mixer restored to 100% baseline"
        } else {
            runRoot("$toolPath boost $p")
        }
        lastHardwareStatus = result.second
        return result
    }

    /** Restore every mixer value captured immediately before hardware boost. */
    fun restoreHardwareMixer(): Pair<Boolean, String> {
        if (detect() != Mode.ROOT) return false to "Root is required to restore the hardware mixer"
        return runRoot("$toolPath restore ${shellQuote(snapshotFile)}")
    }

    fun runPrivileged(command: String): Pair<Boolean, String> {
        return try {
            when (detect()) {
                Mode.ROOT -> runRoot(command)
                Mode.SHIZUKU -> {
                    if (!hasShizukuPermission()) return false to "Shizuku permission not granted"
                    val process = Shizuku.newProcess(arrayOf("sh", "-c", command), null, null)
                    val output = BufferedReader(InputStreamReader(process.inputStream)).use { it.readText() }
                    val error = BufferedReader(InputStreamReader(process.errorStream)).use { it.readText() }
                    (process.waitFor() == 0) to (output + error).trim()
                }
                Mode.NONE -> false to "No privileged backend available"
            }
        } catch (t: Throwable) {
            false to (t.message ?: t.javaClass.simpleName)
        }
    }

    private val toolPath: String
        get() = "${context.applicationInfo.nativeLibraryDir}/libvplus_mixer_tool.so"

    private fun runRoot(command: String): Pair<Boolean, String> = runProcess(arrayOf("su", "-c", command))

    private fun shellQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"

    private fun runProcess(command: Array<String>): Pair<Boolean, String> {
        return try {
            val process = Runtime.getRuntime().exec(command)
            val output = BufferedReader(InputStreamReader(process.inputStream)).use { it.readText() }
            val error = BufferedReader(InputStreamReader(process.errorStream)).use { it.readText() }
            (process.waitFor() == 0) to (output + error).trim()
        } catch (t: Throwable) {
            false to (t.message ?: t.javaClass.simpleName)
        }
    }

    private fun hasRoot(): Boolean = try {
        Runtime.getRuntime().exec(arrayOf("su", "-c", "id")).let { p ->
            val text = BufferedReader(InputStreamReader(p.inputStream)).use { it.readText() }
            p.waitFor() == 0 && text.contains("uid=0")
        }
    } catch (_: Throwable) { false }

    private fun isShizukuAvailable(): Boolean = try {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) false else Shizuku.pingBinder()
    } catch (_: Throwable) { false }

    private fun hasShizukuPermission(): Boolean = try {
        Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED
    } catch (_: Throwable) { false }
}
