package com.aura.verselink

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.aura.verselink.ai.AIProviderFactory

class SettingsActivity : ComponentActivity() {

    private val providerDisplayNames = listOf("Google AI Studio", "OpenAI", "Groq", "Ollama (local)")
    private val providerIds = listOf(
        AIProviderFactory.PROVIDER_GOOGLE,
        AIProviderFactory.PROVIDER_OPENAI,
        AIProviderFactory.PROVIDER_GROQ,
        AIProviderFactory.PROVIDER_OLLAMA
    )

    private val modelsByProvider = mapOf(
        AIProviderFactory.PROVIDER_GOOGLE to listOf(
            "gemma-4-31b-it" to "Gemma 4 31B",
            "gemma-4-26b-a4b-it" to "Gemma 4 26B (Recommended)",
            "gemini-2.0-flash" to "Gemini 2.0 Flash",
            "__custom__" to "Custom…"
        ),
        AIProviderFactory.PROVIDER_OPENAI to listOf(
            "gpt-4o-mini" to "GPT-4o mini",
            "gpt-4o" to "GPT-4o",
            "__custom__" to "Custom…"
        ),
        AIProviderFactory.PROVIDER_GROQ to listOf(
            "llama-3.1-8b-instant" to "Llama 3.1 8B",
            "llama-3.3-70b-versatile" to "Llama 3.3 70B",
            "__custom__" to "Custom…"
        ),
        AIProviderFactory.PROVIDER_OLLAMA to listOf(
            "llama3" to "llama3",
            "mistral" to "mistral",
            "__custom__" to "Custom…"
        )
    )

    private val providerHints = mapOf(
        AIProviderFactory.PROVIDER_GOOGLE to "Get a key at aistudio.google.com",
        AIProviderFactory.PROVIDER_OPENAI to "Get a key at platform.openai.com. Please note that OpenAI models have not been tested in Aura yet and may not work.",
        AIProviderFactory.PROVIDER_GROQ   to "Get a key at console.groq.com",
        AIProviderFactory.PROVIDER_OLLAMA to "Run Ollama locally and enter the device-reachable IP:port above. Please note that Ollama models have not been tested in Aura yet and may not work."
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val prefs = getSharedPreferences("AuraPrefs", Context.MODE_PRIVATE)

        val providerSpinner   = findViewById<Spinner>(R.id.providerSpinner)
        val apiKeyLabel       = findViewById<TextView>(R.id.apiKeyLabel)
        val apiKeyInput       = findViewById<EditText>(R.id.apiKeyInput)
        val ollamaUrlLabel    = findViewById<TextView>(R.id.ollamaUrlLabel)
        val ollamaUrlInput    = findViewById<EditText>(R.id.ollamaUrlInput)
        val modelSpinner      = findViewById<Spinner>(R.id.modelSpinner)
        val customModelInput  = findViewById<EditText>(R.id.customModelInput)
        val saveBtn           = findViewById<Button>(R.id.saveButton)
        val providerHint      = findViewById<TextView>(R.id.providerHint)

        // Provider spinner setup
        val providerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, providerDisplayNames)
        providerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        providerSpinner.adapter = providerAdapter

        val savedProviderId = prefs.getString("provider_id", AIProviderFactory.PROVIDER_GOOGLE) ?: AIProviderFactory.PROVIDER_GOOGLE
        providerSpinner.setSelection(providerIds.indexOf(savedProviderId).coerceAtLeast(0))

        val savedModel = prefs.getString("model_name", "") ?: ""

        fun updateModelSpinner(providerId: String) {
            val models = modelsByProvider[providerId] ?: emptyList()
            val displayNames = models.map { it.second }
            val modelAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, displayNames)
            modelAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            modelSpinner.adapter = modelAdapter

            val knownIndex = models.indexOfFirst { it.first == savedModel }
            if (knownIndex >= 0) {
                modelSpinner.setSelection(knownIndex)
            } else if (savedModel.isNotEmpty()) {
                // Select "Custom…" and pre-fill
                modelSpinner.setSelection(models.size - 1)
                customModelInput.setText(savedModel)
                customModelInput.visibility = View.VISIBLE
            } else {
                modelSpinner.setSelection(0)
            }
        }

        providerSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val providerId = providerIds[position]

                // Update API key label and field
                apiKeyLabel.text = when (providerId) {
                    AIProviderFactory.PROVIDER_GOOGLE -> "Google AI Studio Key"
                    AIProviderFactory.PROVIDER_OPENAI -> "OpenAI API Key"
                    AIProviderFactory.PROVIDER_GROQ   -> "Groq API Key"
                    AIProviderFactory.PROVIDER_OLLAMA -> "API Key (not required)"
                    else -> "API Key"
                }
                apiKeyInput.setText(prefs.getString(AIProviderFactory.apiKeyPrefFor(providerId), ""))

                // Show/hide Ollama URL row
                val isOllama = providerId == AIProviderFactory.PROVIDER_OLLAMA
                ollamaUrlLabel.visibility = if (isOllama) View.VISIBLE else View.GONE
                ollamaUrlInput.visibility = if (isOllama) View.VISIBLE else View.GONE
                if (isOllama) {
                    ollamaUrlInput.setText(prefs.getString("ollama_base_url", "http://localhost:11434"))
                }

                // Update provider hint
                providerHint.text = providerHints[providerId] ?: ""

                updateModelSpinner(providerId)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        modelSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val providerId = providerIds[providerSpinner.selectedItemPosition]
                val models = modelsByProvider[providerId] ?: emptyList()
                val isCustom = models.getOrNull(position)?.first == "__custom__"
                customModelInput.visibility = if (isCustom) View.VISIBLE else View.GONE
                if (!isCustom) customModelInput.text.clear()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        saveBtn.setOnClickListener {
            val providerId = providerIds[providerSpinner.selectedItemPosition]
            val apiKey = apiKeyInput.text.toString().trim()
            val models = modelsByProvider[providerId] ?: emptyList()
            val selectedModelEntry = models.getOrNull(modelSpinner.selectedItemPosition)
            val modelId = if (selectedModelEntry?.first == "__custom__")
                customModelInput.text.toString().trim()
            else
                selectedModelEntry?.first ?: ""

            if (apiKey.isEmpty() && providerId != AIProviderFactory.PROVIDER_OLLAMA) {
                Toast.makeText(this, "Please enter an API key", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (modelId.isEmpty()) {
                Toast.makeText(this, "Please enter a model name", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val editor = prefs.edit()
                .putString("provider_id", providerId)
                .putString(AIProviderFactory.apiKeyPrefFor(providerId), apiKey)
                .putString("model_name", modelId)

            // Keep legacy key in sync when Google is selected
            if (providerId == AIProviderFactory.PROVIDER_GOOGLE) {
                editor.putString("api_key", apiKey)
            }
            if (providerId == AIProviderFactory.PROVIDER_OLLAMA) {
                editor.putString("ollama_base_url", ollamaUrlInput.text.toString().trim())
            }
            editor.apply()

            Toast.makeText(this, "Settings Saved!", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
