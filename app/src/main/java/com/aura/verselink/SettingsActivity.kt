package com.aura.verselink

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.aura.verselink.ai.AIProviderFactory
import com.aura.verselink.ai.ProviderCatalog
import com.aura.verselink.ai.UrlRules
import com.aura.verselink.credits.BillingManager
import com.aura.verselink.credits.CreditsClient
import com.aura.verselink.credits.CreditsException
import com.aura.verselink.credits.CreditsStore
import com.aura.verselink.credits.GoogleSignIn
import com.aura.verselink.credits.CreditsFormat
import kotlinx.coroutines.launch

/**
 * Settings UI rendered entirely from [ProviderCatalog] — provider names,
 * model presets, labels, and hints live in exactly one place, so this
 * screen can never drift out of sync with the factory again.
 *
 * The Aura Credits entry renders an account panel (Google sign-in, balance,
 * credit packs, account deletion) instead of the key/model fields; all BYOK
 * providers render exactly as before.
 */
class SettingsActivity : ComponentActivity() {

    private val providers = ProviderCatalog.providers

    private lateinit var creditsStore: CreditsStore
    private lateinit var creditsClient: CreditsClient
    private var billingManager: BillingManager? = null

    /** Set in onCreate; lets onResume repaint the credits panel after a
     *  background balance refresh. */
    private var refreshCreditsUiCallback: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val prefs = getSharedPreferences("AuraPrefs", Context.MODE_PRIVATE)
        creditsStore = CreditsStore(prefs)
        creditsClient = CreditsClient(creditsStore)

        val providerSpinner = findViewById<Spinner>(R.id.providerSpinner)
        val apiKeyLabel = findViewById<TextView>(R.id.apiKeyLabel)
        val apiKeyInput = findViewById<EditText>(R.id.apiKeyInput)
        val baseUrlLabel = findViewById<TextView>(R.id.baseUrlLabel)
        val baseUrlInput = findViewById<EditText>(R.id.baseUrlInput)
        val modelLabel = findViewById<TextView>(R.id.modelLabel)
        val modelSpinner = findViewById<Spinner>(R.id.modelSpinner)
        val customModelInput = findViewById<EditText>(R.id.customModelInput)
        val saveBtn = findViewById<Button>(R.id.saveButton)
        val providerHint = findViewById<TextView>(R.id.providerHint)

        // Credits account panel views.
        val creditsSection = findViewById<LinearLayout>(R.id.creditsSection)
        val creditsAccountStatus = findViewById<TextView>(R.id.creditsAccountStatus)
        val creditsBalance = findViewById<TextView>(R.id.creditsBalance)
        val creditsSignInButton = findViewById<Button>(R.id.creditsSignInButton)
        val creditsSignOutButton = findViewById<Button>(R.id.creditsSignOutButton)
        val buyCreditsLabel = findViewById<TextView>(R.id.buyCreditsLabel)
        val creditsProductsContainer = findViewById<LinearLayout>(R.id.creditsProductsContainer)
        val creditsProductsStatus = findViewById<TextView>(R.id.creditsProductsStatus)
        val creditsDeleteButton = findViewById<Button>(R.id.creditsDeleteButton)

        fun toast(message: String) =
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()

        fun rebuildProductButtons() {
            creditsProductsContainer.removeAllViews()
            val manager = billingManager ?: return
            val signedIn = creditsStore.hasValidSession()
            for (details in manager.availableProducts) {
                val price = details.oneTimePurchaseOfferDetails?.formattedPrice ?: ""
                val button = Button(this).apply {
                    text = if (price.isNotEmpty()) "${details.name} — $price" else details.name
                    isEnabled = signedIn
                    setOnClickListener { manager.buy(this@SettingsActivity, details) }
                }
                creditsProductsContainer.addView(button)
            }
            val showEmpty = manager.availableProducts.isEmpty()
            creditsProductsStatus.visibility = if (showEmpty) View.VISIBLE else View.GONE
            if (showEmpty) {
                creditsProductsStatus.text = "Credit packs aren't available right now."
            }
        }

        fun refreshCreditsUi() {
            if (creditsStore.hasValidSession()) {
                val email = creditsStore.email()
                creditsAccountStatus.text =
                    if (email != null) "Signed in as $email" else "Signed in"
                creditsBalance.visibility = View.VISIBLE
                creditsBalance.text =
                    "Balance: ${CreditsFormat.formatCredits(creditsStore.cachedBalanceMicros())}"
                creditsSignInButton.visibility = View.GONE
                creditsSignOutButton.visibility = View.VISIBLE
                creditsDeleteButton.visibility = View.VISIBLE
                buyCreditsLabel.visibility = View.VISIBLE
            } else {
                creditsAccountStatus.text = "Not signed in"
                creditsBalance.visibility = View.GONE
                creditsSignInButton.visibility = View.VISIBLE
                creditsSignOutButton.visibility = View.GONE
                creditsDeleteButton.visibility = View.GONE
                buyCreditsLabel.visibility = View.VISIBLE
            }
            rebuildProductButtons()
        }

        fun refreshBalance() {
            if (!creditsStore.hasValidSession()) return
            lifecycleScope.launch {
                try {
                    creditsClient.fetchBalance()
                    refreshCreditsUi()
                } catch (e: CreditsException.SessionExpired) {
                    creditsStore.clear()
                    refreshCreditsUi()
                    toast("Session expired — please sign in again")
                } catch (e: CreditsException) {
                    // Cached balance stays on screen; a failed refresh
                    // isn't worth interrupting the user.
                }
            }
        }

        refreshCreditsUiCallback = { refreshCreditsUi() }

        // Billing: connect as soon as Settings opens so unfinished purchases
        // are recovered and the pack list is ready if credits is selected.
        billingManager = BillingManager(
            context = this,
            creditsClient = creditsClient,
            store = creditsStore,
            scope = lifecycleScope,
            onBalanceUpdated = { runOnUiThread { refreshCreditsUi() } },
            onEvent = { message -> runOnUiThread { toast(message) } }
        ).also { manager ->
            manager.onProductsChanged = { runOnUiThread { rebuildProductButtons() } }
            manager.start()
        }

        creditsSignInButton.setOnClickListener {
            lifecycleScope.launch {
                try {
                    val idToken = GoogleSignIn.requestIdToken(this@SettingsActivity)
                    val result = creditsClient.signInWithGoogle(idToken)
                    toast(
                        if (result.isNewUser) {
                            "Signed in — welcome! 1,000 starter credits were added to your balance."
                        } else {
                            "Signed in"
                        }
                    )
                    refreshCreditsUi()
                } catch (e: CreditsException) {
                    toast(e.message ?: "Sign-in failed")
                }
            }
        }

        creditsSignOutButton.setOnClickListener {
            // Local sign-out only: backend sessions are stateless, so
            // dropping the stored token fully signs this device out.
            creditsStore.clear()
            refreshCreditsUi()
            toast("Signed out")
        }

        creditsDeleteButton.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Delete account and data?")
                .setMessage(
                    "This permanently deletes your Aura Credits account, balance, " +
                        "and purchase history from the server. Credit purchases are " +
                        "not refunded by deleting your account. This can't be undone."
                )
                .setPositiveButton("Delete") { _, _ ->
                    lifecycleScope.launch {
                        try {
                            creditsClient.deleteAccount()
                            creditsStore.clear()
                            refreshCreditsUi()
                            toast("Account deleted")
                        } catch (e: CreditsException.SessionExpired) {
                            creditsStore.clear()
                            refreshCreditsUi()
                            toast("Session expired — please sign in again")
                        } catch (e: CreditsException) {
                            toast("Couldn't delete the account: ${e.message}")
                        }
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

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
                providerHint.text = provider.hint

                if (provider.isCredits) {
                    // Credits has no key, base URL, or model — show the
                    // account panel instead of the BYOK fields.
                    creditsSection.visibility = View.VISIBLE
                    apiKeyLabel.visibility = View.GONE
                    apiKeyInput.visibility = View.GONE
                    baseUrlLabel.visibility = View.GONE
                    baseUrlInput.visibility = View.GONE
                    modelLabel.visibility = View.GONE
                    modelSpinner.visibility = View.GONE
                    customModelInput.visibility = View.GONE
                    refreshCreditsUi()
                    refreshBalance()
                    return
                }

                creditsSection.visibility = View.GONE
                apiKeyLabel.visibility = View.VISIBLE
                apiKeyInput.visibility = View.VISIBLE
                modelLabel.visibility = View.VISIBLE
                modelSpinner.visibility = View.VISIBLE

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

            if (provider.isCredits) {
                // Nothing else to save: sign-in state lives in the credits
                // store, and BYOK providers' keys/models stay untouched.
                prefs.edit().putString("provider_id", provider.id).apply()
                Toast.makeText(this, "Settings Saved!", Toast.LENGTH_SHORT).show()
                stopService(Intent(this, BibleService::class.java))
                startForegroundService(Intent(this, BibleService::class.java))
                finish()
                return@setOnClickListener
            }

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

    override fun onResume() {
        super.onResume()
        // Returning from the Play purchase sheet (or anywhere else): pick up
        // any purchase that finished while we were away, and show a fresh
        // balance.
        billingManager?.processUnfinishedPurchases()
        if (creditsStore.hasValidSession()) {
            lifecycleScope.launch {
                try {
                    creditsClient.fetchBalance()
                    refreshCreditsUiCallback?.invoke()
                } catch (e: CreditsException) {
                    // Keep the cached balance on screen.
                }
            }
        }
    }

    override fun onDestroy() {
        billingManager?.end()
        billingManager = null
        super.onDestroy()
    }
}
