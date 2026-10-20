# Aura Verse Link (AUdio Reference Assistant)

**Aura Verse Link** is an intelligent background utility designed to enhance the sermon-listening experience. By leveraging local speech-to-text and your AI model of choice, the app automatically detects Bible references mentioned in real-time, formats them, and provides a direct notification link to open the passage in YouVersion.

Built with privacy in mind. On compatible devices, the app performs primary audio processing directly on your device. Only the specific, small snippets of text containing a detected Bible reference are sent to AI for formatting. This ensures your data stays in your hands.

## 🚀 How It Works

1.  **Listen:** The app runs silently in the background during a sermon or study.
2.  **Detect:** It listens for specific keywords (Bible books).
3.  **Process:** When a reference is detected, the relevant snippet is sent to AI to be precisely formatted into a standard Bible reference (e.g., "John 3 16" becomes "JHN.3.16").
4.  **Link:** A notification pops up on your device.
5.  **Read:** Tapping the notification opens that exact verse immediately in the **YouVersion Bible App**.

## ✨ Features

* **Hands-Free Experience:** Keep your focus on the sermon without manually searching for verses.
* **Powered by AI:** High-accuracy formatting and context detection.
* **Bring Your Own Provider:** Google, OpenAI, Groq, OpenRouter, DeepSeek, Cerebras, local Ollama, or any other OpenAI-compatible API — one key, your choice of model.
* **Deep Integration:** Direct deep-linking into YouVersion for a seamless transition from audio to text.
* **Lightweight:** Optimized to run in the background with minimal battery impact.

## 🤖 AI Providers

Aura sends only the short transcript snippet around a likely reference to the provider you configure — never continuous audio. Every provider except Google speaks the same OpenAI-compatible Chat Completions format, and every provider's model list in Settings has a **Custom…** option, so a provider renaming a model never breaks the app.

| Provider | Where to get a key | Notes |
|---|---|---|
| **Groq** | [console.groq.com](https://console.groq.com) | Recommended free option. Very fast; free tier is generous. Default model: GPT-OSS 20B. |
| **OpenRouter** | [openrouter.ai/keys](https://openrouter.ai/keys) | One key for hundreds of models from many providers. The default "Free models router" needs no credit balance. Model ids look like `provider/model` — see [openrouter.ai/models](https://openrouter.ai/models). |
| **Google AI Studio** | [aistudio.google.com](https://aistudio.google.com) | Free keys, but the free tier is often rate-limited under load, which is why Groq/OpenRouter are recommended first. Gemma 4 models are the default here. |
| **OpenAI** | [platform.openai.com](https://platform.openai.com) | Pay-as-you-go; the mini/nano models cost a fraction of a cent per reference. |
| **DeepSeek** | [platform.deepseek.com](https://platform.deepseek.com) | Pay-as-you-go and inexpensive. |
| **Cerebras** | [cloud.cerebras.ai](https://cloud.cerebras.ai) | Extremely fast, with a free tier. |
| **Ollama (local)** | No key — run [Ollama](https://ollama.com) yourself | Fully private: nothing leaves your network. Enter the device-reachable base URL in Settings (for a PC on your Wi-Fi: `http://192.168.1.x:11434`, with Ollama started using `OLLAMA_HOST=0.0.0.0`), and pull the model first (`ollama pull llama3.1`). |
| **Custom** | Depends on the server | Any other OpenAI-compatible endpoint: Together, Mistral, xAI, Fireworks, LM Studio (`http://<pc-ip>:1234`), a llama.cpp server, a LiteLLM proxy, etc. Remote servers must use `https://`; plain `http://` is accepted only for local/LAN addresses. |

Your API keys are stored only on your device, are excluded from Android cloud backup and device transfer, and are never written to logs.

## 🛠️ Initial Setup

1.  **Get an API key** from one of the providers above (Groq is the recommended free starting point).
2.  **Configure the App:**
    * The first time you open the app you will be taken to the **Setup** screen.
    * Pick your provider, paste your API key, and choose a model.
    * Grant the necessary permissions for **Microphone** (to hear the sermon) and **Notifications** (to provide the links).

## 📋 Requirements

* An Android device.
* [YouVersion Bible App](https://www.bible.com/app) installed.
* An active internet connection (for AI API calls) — except with a local provider such as Ollama on the same network.
* **Recommended-** For best results and maximum privacy a device with onboard language processing is recommended. This may need to be enabled in your device's settings.

## 🔨 Building

Open in Android Studio, or from the command line with JDK 21 and the Android SDK (platform 35):

```
./gradlew assembleDebug        # build
./gradlew testDebugUnitTest    # unit tests
```

GitHub Actions runs both on every push and pull request.

## 🤝 Contributing

Contributions are welcome! If you have ideas for new features or improvements to the detection logic, feel free to open an issue or submit a pull request.

---
**Disclaimer:** Most of the code for this app was generated by AI under human supervision.
