package com.aura.verselink

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.ComponentActivity

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val input = findViewById<EditText>(R.id.apiKeyInput)
        val saveBtn = findViewById<Button>(R.id.saveButton)
        val prefs = getSharedPreferences("AuraPrefs", Context.MODE_PRIVATE)

        // Pre-fill if key exists
        input.setText(prefs.getString("api_key", ""))

        saveBtn.setOnClickListener {
            val key = input.text.toString().trim()
            if (key.length > 10) {
                prefs.edit().putString("api_key", key).apply()
                Toast.makeText(this, "Key Saved!", Toast.LENGTH_SHORT).show()

                // Start the service immediately
                val serviceIntent = Intent(this, BibleService::class.java)
                startForegroundService(serviceIntent)
                finish()
            } else {
                Toast.makeText(this, "Please enter a valid key", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
