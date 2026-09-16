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
            MaterialTheme {
                VplusScreen()
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun VplusScreen() {
    val engine = remember { AudioBoostEngine() }
    var boost by remember { mutableFloatStateOf(100f) }
    var enabled by remember { mutableStateOf(false) }
    var clarity by remember { mutableStateOf(true) }
    var available by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        available = engine.initialize()
        onDispose { engine.release() }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Text("Video Call Vplus", style = MaterialTheme.typography.headlineMedium)
        Text("Voice volume enhancement for compatible Android audio paths")

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
                Text("100% = normal • 200% = maximum Vplus gain")
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Vplus active", style = MaterialTheme.typography.titleMedium)
                Text(if (available) "Audio effect path available" else "This device/OEM denied the global effect path")
            }
            Switch(
                checked = enabled,
                onCheckedChange = {
                    enabled = it
                    engine.setEnabled(it)
                },
                enabled = available
            )
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Voice clarity EQ", style = MaterialTheme.typography.titleMedium)
                Text("Speech-focused equalization with conservative boosts")
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

        Button(
            onClick = {
                available = engine.initialize()
                engine.setBoostPercent(boost.toInt())
                engine.setVoiceClarity(clarity)
                engine.setEnabled(enabled)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Re-detect audio path")
        }

        Text(
            "Distortion protection: gain is capped at +6 dB and the Android LoudnessEnhancer " +
                "processing path is used. Actual maximum output remains hardware/OEM dependent.",
            style = MaterialTheme.typography.bodySmall
        )
    }
}
