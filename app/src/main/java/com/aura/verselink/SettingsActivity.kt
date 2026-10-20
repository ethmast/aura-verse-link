package com.aura.verselink

import android.content.Context
import android.content.Intent
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
import com.aura.verselink.ai.ProviderCatalog
import com.aura.verselink.ai.UrlRules

/**
 * Settings UI rendered entirely from [ProviderCatalog] — provider names,
 * model presets, labels, and hints live in exactly one place, so this
 * screen can never drift out of sync with the factory again.
 */
class SettingsActivity : ComponentActivity() {

    private val providers = ProviderCatalog.providers

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val prefs = getSharedPreferences("AuraPrefs", Context.MODE_PRIVATE)

        val providerSpinner = findViewById<Spinner>(R.id.providerSpinner)
        val apiKeyLabel = findViewById<TextView>(R.id.apiKeyLabel)
        val apiKeyInput = findViewById<EditText>(R.id.apiKeyInput)
        val baseUrlLabel = findViewById<TextView>(R.id.baseUrlLabel)
        val baseUrlInput = findViewById<EditText>(R.id.baseUrlInput)
        val modelSpinner = findViewById<Spinner>(R.id.modelSpinner)
        val customModelInput = findViewById<EditText>(R.id.customModelInput)
        val saveBtn = findViewById<Button>(R.id.saveButton)
        val providerHint = findViewById<TextView>(R.id.providerHint)

        fun spinnerAdapter(items: List<String>): ArrayAdapter<String> =
            ArrayAdapter(this, android.R.layout.simple_spinner_item, items).also {
                it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }

        /** Model entries for a provider: its presets plus a trailing "Custom…". */
        fun modelEntries(provider: ProviderCatalog.Provider): List<ProviderCatalog.ModelOption> =
            provider.models + ProviderCatalog.ModelOption(ProviderCatalog.CUSTOM_MODEL, "Custom…")

        fun updateModelSpinner(provider: ProviderCatalog.Provider) {
            val entries = modelEntries(provider)
            modelSpinner.adapter = spinnerAdapter(entries.map { it.label })

            val savedModel = AIProviderFactory.savedModel(prefs, provider.id) ?: ""
            val knownIndex = entries.indexOfFirst { it.id == savedModel }
            when {
                provider.models.isEmpty() -> {
                    // Custom provider: the model is always typed in.
                    modelSpinner.setSelection(entries.lastIndex)
                    customModelInput.setText(savedModel)
                    customModelInput.visibility = View.VISIBLE
                }
                knownIndex >= 0 -> {
                    modelSpinner.setSelection(knownIndex)
                    customModelInput.visibility = View.GONE
                }
                savedModel.isNotEmpty() -> {
                    modelSpinner.setSelection(entries.lastIndex)
                    customModelInput.setText(savedModel)
                    customModelInput.visibility = View.VISIBLE
                }
                else -> modelSpinner.setSelection(0)
            }
        }

        providerSpinner.adapter = spinnerAdapter(providers.map { it.displayName })
        val savedProviderId = prefs.getString("provider_id", ProviderCatalog.GOOGLE) ?: ProviderCatalog.GOOGLE
        providerSpinner.setSelection(
            providers.indexOfFirst { it.id == savedProviderId }.coerceAtLeast(0)
        )

        providerSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val provider = providers[position]

                apiKeyLabel.text = provider.apiKeyLabel
                apiKeyInput.setText(prefs.getString(AIProviderFactory.apiKeyPrefFor(provider.id), ""))

                val showBaseUrl = provider.allowsCustomBaseUrl
                baseUrlLabel.visibility = if (showBaseUrl) View.VISIBLE else View.GONE
                baseUrlInput.visibility = if (showBaseUrl) View.VISIBLE else View.GONE
                if (showBaseUrl) {
                    val savedUrl = prefs.getString(AIProviderFactory.baseUrlPrefFor(provider.id), null)
                        ?: if (provider.id == ProviderCatalog.OLLAMA) {
                            prefs.getString("ollama_base_url", null)
                        } else null
                    baseUrlInput.setText(savedUrl ?: provider.baseUrl ?: "")
                    baseUrlInput.hint = provider.baseUrl ?: "https://example.com/v1"
                }

                providerHint.text = provider.hint
                updateModelSpinner(provider)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        modelSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val provider = providers[providerSpinner.selectedItemPosition]
                val entries = modelEntries(provider)
                val isCustom = entries.getOrNull(position)?.id == ProviderCatalog.CUSTOM_MODEL
                customModelInput.visibility = if (isCustom) View.VISIBLE else View.GONE
                if (!isCustom) customModelInput.text.clear()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        saveBtn.setOnClickListener {
            val provider = providers[providerSpinner.selectedItemPosition]
            val apiKey = apiKeyInput.text.toString().trim()
            val entries = modelEntries(provider)
            val selected = entries.getOrNull(modelSpinner.selectedItemPosition)
            val modelId = if (selected?.id == ProviderCatalog.CUSTOM_MODEL || provider.models.isEmpty()) {
                customModelInput.text.toString().trim()
            } else {
                selected?.id ?: ""
            }

            if (provider.requiresApiKey && apiKey.isEmpty()) {
                Toast.makeText(this, "Please enter an API key", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (modelId.isEmpty()) {
                Toast.makeText(this, "Please enter a model name", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            var normalizedBaseUrl: String? = null
            if (provider.allowsCustomBaseUrl) {
                normalizedBaseUrl = UrlRules.normalizeBaseUrl(baseUrlInput.text.toString())
                if (normalizedBaseUrl == null) {
                    Toast.makeText(
                        this,
                        "That base URL can't be used. Remote servers need https://; " +
                            "http:// is only allowed for local/LAN addresses.",
                        Toast.LENGTH_LONG
                    ).show()
                    return@setOnClickListener
                }
            }

            val editor = prefs.edit()
                .putString("provider_id", provider.id)
                .putString(AIProviderFactory.apiKeyPrefFor(provider.id), apiKey)
                .putString(AIProviderFactory.modelPrefFor(provider.id), modelId)
                // Legacy shared model pref, kept in sync for older code paths.
                .putString("model_name", modelId)

            if (normalizedBaseUrl != null) {
                editor.putString(AIProviderFactory.baseUrlPrefFor(provider.id), normalizedBaseUrl)
                if (provider.id == ProviderCatalog.OLLAMA) {
                    editor.putString("ollama_base_url", normalizedBaseUrl)
                }
            }
            // Keep the legacy key in sync when Google is selected (older versions read it).
            if (provider.id == ProviderCatalog.GOOGLE) {
                editor.putString("api_key", apiKey)
            }
            editor.apply()

            Toast.makeText(this, "Settings Saved!", Toast.LENGTH_SHORT).show()
            stopService(Intent(this, BibleService::class.java))
            startForegroundService(Intent(this, BibleService::class.java))
            finish()
        }
    }
}
