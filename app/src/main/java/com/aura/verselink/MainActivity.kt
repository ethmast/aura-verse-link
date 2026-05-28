package com.aura.verselink

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aura.verselink.ai.AIProviderFactory
import com.aura.verselink.ui.theme.AuraVerseLinkTheme

class MainActivity : ComponentActivity() {

    override fun onStart() {
        super.onStart()
        val prefs = getSharedPreferences("AuraPrefs", Context.MODE_PRIVATE)
        val providerId = prefs.getString("provider_id", null)
        val hasLegacyKey = !prefs.getString("api_key", null).isNullOrEmpty()
        val hasNewKey = providerId != null && (
            !prefs.getString(AIProviderFactory.apiKeyPrefFor(providerId), null).isNullOrEmpty()
            || providerId == AIProviderFactory.PROVIDER_OLLAMA
        )
        if (!hasLegacyKey && !hasNewKey) {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val permissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { }

        permissionLauncher.launch(arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.POST_NOTIFICATIONS
        ))

        setContent {
            var isServiceRunning by remember { mutableStateOf(isServiceRunning(BibleService::class.java)) }
            var showLogs by remember { mutableStateOf(false) }
            val logs by BibleService.logFlow.collectAsState()
            val coroutineScope = rememberCoroutineScope()

            AuraVerseLinkTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Aura Verse Link",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Automatically detect and open Bible verses mentioned in your environment using Gemini AI.",
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(48.dp))

                        if (isServiceRunning) {
                            Button(
                                onClick = {
                                    stopService(Intent(this@MainActivity, BibleService::class.java))
                                    isServiceRunning = false
                                },
                                modifier = Modifier.fillMaxWidth().height(56.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                                )
                            ) {
                                Icon(Icons.Default.MicOff, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Disable Listener")
                            }
                        } else {
                            Button(
                                onClick = {
                                    val audioGranted = ContextCompat.checkSelfPermission(
                                        this@MainActivity, Manifest.permission.RECORD_AUDIO
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (audioGranted) {
                                        startForegroundService(Intent(this@MainActivity, BibleService::class.java))
                                        isServiceRunning = true
                                    } else {
                                        permissionLauncher.launch(arrayOf(
                                            Manifest.permission.RECORD_AUDIO,
                                            Manifest.permission.POST_NOTIFICATIONS
                                        ))
                                        Toast.makeText(
                                            this@MainActivity,
                                            "Microphone permission required — please grant it and try again.",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(56.dp)
                            ) {
                                Icon(Icons.Default.Mic, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Enable Listener")
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            TextButton(onClick = {
                                startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
                            }) {
                                Text("Settings")
                            }

                            TextButton(onClick = { showLogs = !showLogs }) {
                                Text(if (showLogs) "Hide Logs" else "Show Logs")
                            }
                        }

                        OutlinedButton(
                            onClick = {
                                coroutineScope.launch {
                                    BibleService.testTrigger.emit("john three 16")
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Test: \"John three 16\"")
                        }

                        if (showLogs) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                if (logs.isEmpty()) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            "No logs yet",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                } else {
                                    LazyColumn(modifier = Modifier.padding(8.dp)) {
                                        items(logs) { entry ->
                                            Text(
                                                text = entry,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontFamily = FontFamily.Monospace,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun isServiceRunning(serviceClass: Class<*>): Boolean {
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        for (service in manager.getRunningServices(Int.MAX_VALUE)) {
            if (serviceClass.name == service.service.className) return true
        }
        return false
    }
}
