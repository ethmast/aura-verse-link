package com.aura.verselink

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aura.verselink.ui.theme.AuraVerseLinkTheme

class PrivacyPolicyActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AuraVerseLinkTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    PrivacyPolicyScreen()
                }
            }
        }
    }
}

@Composable
private fun PrivacyPolicyScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "Privacy Policy",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Last updated: May 28, 2026",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(20.dp))

        Section("Microphone Access") {
            "Aura Verse Link uses your device's microphone to listen for spoken Bible verse references " +
            "(e.g. \"John 3:16\") during sermons or study sessions. Audio is processed entirely on your " +
            "device using Android's built-in speech recognition. No audio is stored or transmitted by this app."
        }
        Section("Text Snippets Sent to AI") {
            "When a potential Bible verse reference is detected in the transcribed text, a short text " +
            "snippet is sent to the AI provider you configured (Google AI Studio, OpenAI, Groq, or a " +
            "local Ollama instance) solely to format the reference correctly. The developer of this app " +
            "does not receive, store, or access any of this data."
        }
        Section("API Keys") {
            "Your AI provider API key is stored locally on your device in app-private storage. It is " +
            "never transmitted to the developer or any party other than the AI provider you selected."
        }
        Section("Third-Party Services") {
            "Depending on your AI provider choice, text snippets may be sent to:\n\n" +
            "• Google AI Studio (ai.google.dev) — subject to Google's privacy policy\n" +
            "• OpenAI (openai.com) — subject to OpenAI's privacy policy\n" +
            "• Groq (groq.com) — subject to Groq's privacy policy\n" +
            "• Ollama — processed entirely on your local network, no data leaves your device"
        }
        Section("Data We Collect") {
            "The developer of Aura Verse Link does not collect, store, or share any personal data."
        }
        Section("Contact") {
            "For questions about this privacy policy, contact: aura@lavernmast.com"
        }
    }
}

@Composable
private fun Section(title: String, body: () -> String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold
    )
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = body(),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(16.dp))
}
