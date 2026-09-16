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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme { VplusScreen() }
        }
    }
}

@androidx.compose.runtime.Composable
private fun VplusScreen() {
    val engine = remember { AudioBoostEngine() }
    val privileged = remember { PrivilegedAudioBackend() }
    var boost by remember { mutableFloatStateOf(100f) }
    var enabled by remember { mutableStateOf(false) }
    var clarity by remember { mutableStateOf(true) }
    var available by remember { mutableStateOf(false) }
    var privilegedMode by remember { mutableStateOf(PrivilegedAudioBackend.Mode.NONE) }

    DisposableEffect(Unit) {
        available = engine.initialize()
        privilegedMode = privileged.detect()
        if (privilegedMode != PrivilegedAudioBackend.Mode.NONE) privileged.maximizeCallVolume()
        onDispose { engine.release() }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Text("Video Call Vplus", style = MaterialTheme.typography.headlineMedium)
        Text("Aggressive voice-volume enhancement with optional privileged audio access")

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Vplus amplification: ${boost.toInt()}%", style = MaterialTheme.typography.titleLarge)
                Slider(
                    value = boost,
                    onValueChange = {
                        boost = it
                        engine.setBoostPercent(it.toInt())
                    },
                    valueRange = 100f..200f,
                    steps = 19,
                    enabled = available
                )
                Text("100% = normal • 200% = maximum Vplus DSP target")
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Vplus active", style = MaterialTheme.typography.titleMedium)
                Text(if (available) "Audio effect path available" else "System audio effect path unavailable")
            }
            Switch(
                checked = enabled,
                onCheckedChange = {
                    enabled = it
                    engine.setEnabled(it)
                    if (it) privileged.maximizeCallVolume()
                },
                enabled = available
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
                        PrivilegedAudioBackend.Mode.ROOT -> "Root available — privileged volume path enabled"
                        PrivilegedAudioBackend.Mode.SHIZUKU -> "Shizuku available — privileged shell path enabled"
                        PrivilegedAudioBackend.Mode.NONE -> "Root/Shizuku not available — public Android path only"
                    }
                )
            }
        }

        Button(
            onClick = {
                available = engine.initialize()
                engine.setBoostPercent(boost.toInt())
                engine.setVoiceClarity(clarity)
                engine.setEnabled(enabled)
                privilegedMode = privileged.detect()
                if (privilegedMode != PrivilegedAudioBackend.Mode.NONE) privileged.maximizeCallVolume()
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Re-detect and force maximum call path") }

        Text(
            "Distortion protection: the boost is bounded and speech EQ is conservative. " +
                "The privileged layer first maximizes the Android voice/media stream; DSP gain then provides additional amplification where the device permits the effect session.",
            style = MaterialTheme.typography.bodySmall
        )
    }
}
