package com.tbzmike.vplus

import android.os.Build
import java.io.BufferedReader
import java.io.InputStreamReader
import rikka.shizuku.Shizuku

/** Optional root/Shizuku execution layer. It reports failures instead of pretending to bypass audio policy. */
class PrivilegedAudioBackend {
    enum class Mode { NONE, ROOT, SHIZUKU }

    var mode: Mode = Mode.NONE
        private set

    fun detect(): Mode {
        if (hasRoot()) {
            mode = Mode.ROOT
            return mode
        }
        mode = if (isShizukuAvailable()) Mode.SHIZUKU else Mode.NONE
        return mode
    }

    fun isAvailable(): Boolean = detect() != Mode.NONE

    /** Raise the media stream to the platform maximum before DSP amplification. */
    fun maximizeMediaVolume(): Boolean {
        return runPrivileged("cmd media_session volume --stream 3 --set 15").first
    }

    fun runPrivileged(command: String): Pair<Boolean, String> {
        return try {
            when (detect()) {
                Mode.ROOT -> runProcess(arrayOf("su", "-c", command))
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

    private fun runProcess(command: Array<String>): Pair<Boolean, String> {
        val process = Runtime.getRuntime().exec(command)
        val output = BufferedReader(InputStreamReader(process.inputStream)).use { it.readText() }
        val error = BufferedReader(InputStreamReader(process.errorStream)).use { it.readText() }
        return (process.waitFor() == 0) to (output + error).trim()
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
