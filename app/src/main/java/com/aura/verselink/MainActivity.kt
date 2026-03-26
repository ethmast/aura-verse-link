package com.aura.verselink

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aura.verselink.ui.theme.AuraVerseLinkTheme

class MainActivity : ComponentActivity() {


    override fun onStart() {
        super.onStart()
        val prefs = getSharedPreferences("AuraPrefs", Context.MODE_PRIVATE)
        if (prefs.getString("api_key", null) == null) {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request Permissions
        val permissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            // Permissions handled by system dialog
        }

        permissionLauncher.launch(arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.POST_NOTIFICATIONS
        ))

        setContent {
            var isServiceRunning by remember { mutableStateOf(isServiceRunning(BibleService::class.java)) }

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
                                    val intent = Intent(this@MainActivity, BibleService::class.java)
                                    startForegroundService(intent)
                                    isServiceRunning = true
                                },
                                modifier = Modifier.fillMaxWidth().height(56.dp)
                            ) {
                                Icon(Icons.Default.Mic, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Enable Listener")
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
            if (serviceClass.name == service.service.className) {
                return true
            }
        }
        return false
    }
}
