package com.tbzmike.vplus

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme { VplusScreen() }
        }
    }
}

@Composable
private fun VplusScreen() {
    val context = LocalContext.current
    val engine = remember { AudioBoostEngine() }
    val privileged = remember { PrivilegedAudioBackend(context.applicationContext) }
    var boost by remember { mutableFloatStateOf(100f) }
    var enabled by remember { mutableStateOf(false) }
    var clarity by remember { mutableStateOf(true) }
    var available by remember { mutableStateOf(false) }
    var privilegedMode by remember { mutableStateOf(PrivilegedAudioBackend.Mode.NONE) }
    var hardwareStatus by remember { mutableStateOf("Not scanned") }

    DisposableEffect(Unit) {
        available = engine.initialize()
        privilegedMode = privileged.detect()
        if (privilegedMode == PrivilegedAudioBackend.Mode.ROOT) {
            hardwareStatus = privileged.scanHardwareMixer().second
        }
        onDispose {
            if (enabled && privilegedMode == PrivilegedAudioBackend.Mode.ROOT) privileged.restoreHardwareMixer()
            engine.release()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Text("Video Call Vplus", style = MaterialTheme.typography.headlineMedium)
        Text("Layered voice-volume enhancement with root/Shizuku support")

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Vplus amplification: ${boost.toInt()}%", style = MaterialTheme.typography.titleLarge)
                Slider(
                    value = boost,
                    onValueChange = {
                        boost = it
                        engine.setBoostPercent(it.toInt())
                        if (enabled && privilegedMode == PrivilegedAudioBackend.Mode.ROOT) {
                            hardwareStatus = privileged.setHardwareBoost(it.toInt()).second
                        }
                    },
                    valueRange = 100f..200f,
                    steps = 19,
                    enabled = available || privilegedMode != PrivilegedAudioBackend.Mode.NONE
                )
                Text("100% = normal • 200% = maximum Vplus target")
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Vplus active", style = MaterialTheme.typography.titleMedium)
                Text(
                    when {
                        privilegedMode == PrivilegedAudioBackend.Mode.ROOT -> "Root hardware path available"
                        available -> "Android DSP path available"
                        else -> "No usable audio backend detected"
                    }
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = {
                    enabled = it
                    if (it) {
                        engine.setBoostPercent(boost.toInt())
                        engine.setVoiceClarity(clarity)
                        engine.setEnabled(true)
                        if (privilegedMode == PrivilegedAudioBackend.Mode.ROOT) {
                            val result = privileged.maximizeCallVolume(boost.toInt())
                            hardwareStatus = result.second
                        } else if (privilegedMode == PrivilegedAudioBackend.Mode.SHIZUKU) {
                            privileged.maximizeCallVolume(boost.toInt())
                        }
                    } else {
                        engine.setEnabled(false)
                        if (privilegedMode == PrivilegedAudioBackend.Mode.ROOT) {
                            hardwareStatus = privileged.restoreHardwareMixer().second
                        }
                    }
                },
                enabled = available || privilegedMode != PrivilegedAudioBackend.Mode.NONE
            )
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Voice clarity EQ", style = MaterialTheme.typography.titleMedium)
                Text("Speech-focused equalization with controlled boosts")
            }
            Switch(
                checked = clarity,
                onCheckedChange = {
                    clarity = it
                    engine.setVoiceClarity(it)
                },
                enabled = available
            )
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Privileged audio layer", style = MaterialTheme.typography.titleMedium)
                Text(
                    when (privilegedMode) {
                        PrivilegedAudioBackend.Mode.ROOT -> "Root available — direct ALSA/TinyALSA path enabled"
                        PrivilegedAudioBackend.Mode.SHIZUKU -> "Shizuku available — platform volume path enabled"
                        PrivilegedAudioBackend.Mode.NONE -> "Root/Shizuku not available — public Android path only"
                    }
                )
                Text("Hardware scan: $hardwareStatus", style = MaterialTheme.typography.bodySmall)
            }
        }

        Button(
            onClick = {
                available = engine.initialize()
                engine.setBoostPercent(boost.toInt())
                engine.setVoiceClarity(clarity)
                engine.setEnabled(enabled)
                privilegedMode = privileged.detect()
                hardwareStatus = if (privilegedMode == PrivilegedAudioBackend.Mode.ROOT) {
                    privileged.scanHardwareMixer().second
                } else "Root unavailable"
                if (enabled && privilegedMode != PrivilegedAudioBackend.Mode.NONE) {
                    privileged.maximizeCallVolume(boost.toInt())
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Re-detect audio backends") }

        Text(
            "Hardware controls are discovered from the phone at runtime. Vplus does not assume Qualcomm mixer names or ranges. " +
                "The hardware boost is deliberately conservative and can restore the captured mixer values.",
            style = MaterialTheme.typography.bodySmall
        )
    }
}
