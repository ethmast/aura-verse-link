package com.aura.verselink.ai

import java.net.URI

/**
 * Rules for user-supplied base URLs (Ollama, Custom provider).
 *
 * Remote endpoints must use https. Plain http is accepted only for
 * loopback / private-LAN hosts, which is exactly the local-server case
 * (Ollama, LM Studio, llama.cpp) that needs it. The manifest enables
 * cleartext traffic for those calls, so this check is what keeps a
 * typo'd or hostile http:// URL from sending an API key in the clear.
 */
object UrlRules {

    /**
     * Normalizes a user-entered base URL for an OpenAI-compatible API:
     * trims whitespace and trailing slashes, strips an accidentally pasted
     * trailing "/chat/completions", and appends "/v1" when the URL is a bare
     * host (no path), since every compatible server mounts the API there.
     * Returns null when the URL is unusable.
     */
    fun normalizeBaseUrl(raw: String?): String? {
        var url = raw?.trim()?.trimEnd('/') ?: return null
        if (url.isEmpty()) return null
        if (url.endsWith("/chat/completions")) {
            url = url.removeSuffix("/chat/completions").trimEnd('/')
        }
        val uri = try {
            URI(url)
        } catch (e: Exception) {
            return null
        }
        if (uri.scheme == null || uri.host == null) return null
        if (uri.scheme != "http" && uri.scheme != "https") return null
        if (!isUrlAllowed(url)) return null
        val path = uri.path?.trimEnd('/') ?: ""
        return if (path.isEmpty()) "$url/v1" else url
    }

    /** https anywhere; http only for loopback / private / link-local / .local hosts. */
    fun isUrlAllowed(url: String): Boolean {
        val uri = try {
            URI(url.trim())
        } catch (e: Exception) {
            return false
        }
        return when (uri.scheme) {
            "https" -> uri.host != null
            "http" -> uri.host != null && isLocalHost(uri.host!!)
            else -> false
        }
    }

    fun isLocalHost(host: String): Boolean {
        val h = host.trim().trim('[', ']').lowercase()
        return h == "localhost" ||
            h.endsWith(".local") ||
            h == "::1" ||
            isPrivateIpv4(h)
    }

    private fun isPrivateIpv4(host: String): Boolean {
        val parts = host.split(".")
        if (parts.size != 4) return false
        val octets = parts.map { it.toIntOrNull() ?: return false }
        if (octets.any { it !in 0..255 }) return false
        val (a, b) = octets
        return a == 127 ||                       // loopback
            a == 10 ||                           // 10.0.0.0/8
            (a == 172 && b in 16..31) ||         // 172.16.0.0/12
            (a == 192 && b == 168) ||            // 192.168.0.0/16
            (a == 169 && b == 254)               // link-local
    }
}
