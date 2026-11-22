package com.rykocollection.app.safeguard

import android.net.Uri
import java.net.IDN
import java.net.URI
import java.util.Locale

internal enum class NavigationAction { ALLOW, ASK, BLOCK }

internal data class NavigationDecision(
    val action: NavigationAction,
    val host: String,
    val reason: String
)

internal object NavigationPolicy {
    private val blockedHosts = setOf(
        "doubleclick.net", "googlesyndication.com", "googleadservices.com",
        "adservice.google.com", "adnxs.com", "adsrvr.org", "advertising.com",
        "adform.net", "adroll.com", "criteo.com", "taboola.com", "outbrain.com",
        "popads.net", "popcash.net", "propellerads.com", "exoclick.com",
        "adsterra.com", "yllix.com", "trafficjunky.net", "mgid.com",
        "revcontent.com", "zedo.com", "smartadserver.com", "rubiconproject.com",
        "openx.net", "pubmatic.com", "casalemedia.com", "lijit.com",
        "sharethrough.com", "bidvertiser.com", "hilltopads.net", "onclickads.net",
        "pushengage.com", "pushwoosh.com", "ad-maven.com", "juicyads.com",
        "adcash.com", "popunder.net", "onclickmega.com", "trafficstars.com",
        "adf.ly", "shorte.st", "clk.sh", "linkvertise.com", "loot-link.com",
        "lootlinks.co", "adsco.re", "adfoc.us", "cointraffic.io", "monetag.com",
        "galaksion.com", "dtscout.com", "tsyndicate.com", "exosrv.com",
        "adnium.com", "adspyglass.com", "adskeeper.com", "ad-delivery.net",
        "adnami.io", "admanmedia.com", "admedo.com", "adnetwork.vn"
    )

    fun normalize(raw: String): String? {
        val candidate = raw.trim().let {
            if (it.contains("://")) it else "https://$it"
        }
        return try {
            val parsed = URI(candidate)
            val scheme = parsed.scheme?.lowercase(Locale.ROOT)
            val host = parsed.host ?: return null
            if (scheme !in setOf("http", "https") || host.isBlank()) return null
            val asciiHost = IDN.toASCII(host, IDN.USE_STD3_ASCII_RULES).lowercase(Locale.ROOT)
            URI(scheme, parsed.userInfo, asciiHost, parsed.port, parsed.path, parsed.query, parsed.fragment).toASCIIString()
        } catch (_: Exception) {
            null
        }
    }

    fun hostOf(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        return try {
            val host = Uri.parse(raw).host ?: return ""
            IDN.toASCII(host, IDN.USE_STD3_ASCII_RULES).lowercase(Locale.ROOT)
        } catch (_: Exception) { "" }
    }

    fun isSameSite(first: String, second: String): Boolean {
        return first.isNotBlank() && second.isNotBlank() && first.equals(second, ignoreCase = true)
    }

    fun isWithinTrustedDomain(host: String, domain: String): Boolean {
        val normalizedHost = host.lowercase(Locale.ROOT)
        val normalizedDomain = domain.lowercase(Locale.ROOT).removePrefix("www.")
        return normalizedHost == normalizedDomain || normalizedHost.endsWith(".$normalizedDomain")
    }

    fun isBlockedHost(host: String): Boolean {
        val normalized = host.lowercase(Locale.ROOT).removePrefix("www.")
        return blockedHosts.any { normalized == it || normalized.endsWith(".$it") }
    }

    fun decide(
        destination: String,
        currentUrl: String?,
        userGesture: Boolean,
        trustedDomains: Set<String>,
        protectionEnabled: Boolean
    ): NavigationDecision {
        val host = hostOf(destination)
        if (host.isBlank()) return NavigationDecision(NavigationAction.BLOCK, "", "Invalid destination")
        if (protectionEnabled && isBlockedHost(host)) {
            return NavigationDecision(NavigationAction.BLOCK, host, "Known advertising or redirect domain")
        }
        if (!protectionEnabled) return NavigationDecision(NavigationAction.ALLOW, host, "Protection is paused")
        if (trustedDomains.any { isWithinTrustedDomain(host, it) }) {
            return NavigationDecision(NavigationAction.ALLOW, host, "Trusted website")
        }
        val currentHost = hostOf(currentUrl)
        if (isSameSite(host, currentHost)) {
            return NavigationDecision(NavigationAction.ALLOW, host, "Same website")
        }
        if (userGesture) return NavigationDecision(NavigationAction.ALLOW, host, "Direct user navigation")
        return NavigationDecision(NavigationAction.BLOCK, host, "Automatic cross-site redirect blocked")
    }
}

internal class RedirectTracker {
    var currentTopLevelUrl: String? = null
        private set

    fun onPageStarted(url: String) {
        currentTopLevelUrl = url
    }

    fun currentHost(): String = NavigationPolicy.hostOf(currentTopLevelUrl)
}
