package com.example.adblock

import android.net.Uri

object AdBlocker {
    private val AD_DOMAINS = hashSetOf(
        "doubleclick.net",
        "googlesyndication.com",
        "googleads.g.doubleclick.net",
        "pagead2.googlesyndication.com",
        "adservice.google.com",
        "adnxs.com",
        "pubmatic.com",
        "popads.net",
        "exoclick.com",
        "mgid.com",
        "outbrain.com",
        "taboola.com",
        "criteo.com",
        "amazon-adsystem.com",
        "adsystem.com",
        "scorecardresearch.com",
        "quantserve.com",
        "adroll.com",
        "rubiconproject.com",
        "openx.net",
        "adcolony.com",
        "applovin.com",
        "unity3dsales.com",
        "adsrvr.org",
        "bidswitch.net",
        "smartadserver.com",
        "zedo.com",
        "popcash.net",
        "adsterra.com",
        "propellerads.com",
        "amung.us",
        "histats.com"
    )

    private val AD_KEYWORDS = listOf(
        "googleads",
        "doubleclick",
        "/ads/",
        "/ad/",
        "/advertisement/",
        "ad_slot",
        "adslot",
        "popunder",
        "pop-ads",
        "onclickads",
        "exoclick",
        "taboola",
        "outbrain",
        "ad-system",
        "ad_system",
        "analytics.js",
        "google-analytics",
        "adsbygoogle",
        "adsbox",
        "ad-banner",
        "adbanner",
        "sponsored",
        "trackers",
        "telemetry"
    )

    /**
     * Determines if a loading resource url belongs to an advertising domain, tracking script,
     * or popup partner.
     */
    fun isAd(url: String?): Boolean {
        if (url.isNullOrEmpty()) return false
        val cleanUrl = url.lowercase()

        // 1. Direct host matching
        try {
            val uri = Uri.parse(url)
            val host = uri.host?.lowercase() ?: ""
            for (domain in AD_DOMAINS) {
                if (host == domain || host.endsWith(".$domain")) {
                    return true
                }
            }
        } catch (e: Exception) {
            // fail-silent parsing error
        }

        // 2. Keyword substring matching
        for (keyword in AD_KEYWORDS) {
            if (cleanUrl.contains(keyword)) {
                return true
            }
        }
        return false
    }

    /**
     * JavaScript snippet that queries outstanding advertisement frames/elements on page finished loading
     * and sets their CSS styling to 'display: none' to hide spaces where ads would otherwise render.
     */
    fun getAdHideScript(): String {
        return """
            (function() {
                var selectors = [
                    '[class*="ad-"]', '[class*="-ad"]', '[class*="ad_"]', '[class*="_ad"]',
                    '[id*="ad-"]', '[id*="-ad"]', '[id*="ad_"]', '[id*="_ad"]',
                    'iframe[src*="googleads"]', 'iframe[src*="doubleclick"]',
                    '.ad-container', '.ad-wrapper', '.ad-slot', '.adsbox', '.ad_box',
                    '#ad-container', '#ad-wrapper', '#ad-slot', '#adsbox',
                    '[class*="sponsor"]', '[id*="sponsor"]', '[class*="sponsored"]', '[id*="sponsored"]',
                    '.trc_rbox_container', '.outbrain', '.taboola',
                    'div[id^="google_ads_iframe"]', 'div[id^="div-gpt-ad"]',
                    'amp-ad', 'ins.adsbygoogle'
                ];
                function hideAds() {
                    selectors.forEach(function(sel) {
                        try {
                            var elements = document.querySelectorAll(sel);
                            elements.forEach(function(el) {
                                if (el && el.style.display !== 'none') {
                                    el.style.display = 'none';
                                    el.style.height = '0px';
                                    el.style.width = '0px';
                                    el.style.opacity = '0';
                                    el.style.pointerEvents = 'none';
                                }
                            });
                        } catch(e) {}
                    });
                }
                hideAds();
                // Repeat after intervals to catch dynamically-inserted ad widgets
                setTimeout(hideAds, 500);
                setTimeout(hideAds, 1500);
                setTimeout(hideAds, 5000);
            })();
        """.trimIndent()
    }

    /**
     * Custom script to completely remove the annoying YouTube sign-in requests, cookie popups,
     * app upsells (Get the app/Use the web), and force general background video viewing.
     */
    fun getYoutubeCleanScript(): String {
        return """
            (function() {
                var style = document.createElement('style');
                style.id = 'yt-bypass-overlay-styles';
                style.innerHTML = ' \
                    ytm-mealbar-promo-renderer, \
                    ytm-upsell-dialog-renderer, \
                    ytm-consent-bump-renderer, \
                    ytm-cookie-consent-renderer, \
                    .yt-dialog-renderer, \
                    ytd-consent-bump-v2-renderer, \
                    .eom-cookie-consent-container, \
                    ytd-popup-container, \
                    yt-upsell-dialog-renderer, \
                    yt-m-upsell-dialog-renderer, \
                    .consent-bump, \
                    .upsell-dialog, \
                    .mealbar-promo-renderer, \
                    #dialog, \
                    #consent-bump { \
                        display: none !important; \
                    } \
                    html, body { \
                        overflow: auto !important; \
                        height: auto !important; \
                        position: static !important; \
                    } \
                ';
                if (!document.getElementById('yt-bypass-overlay-styles')) {
                    document.head.appendChild(style);
                }

                function dismissPromo() {
                    // Auto-click "not now", "no thanks", or "not signing in" options on mobile YT dialogs
                    var buttons = document.querySelectorAll('button, [role="button"], a');
                    buttons.forEach(function(btn) {
                        var txt = (btn.textContent || btn.innerText || "").toLowerCase().trim();
                        if (txt === "no thanks" || txt === "not now" || txt === "dismiss" || txt === "reject all" || txt === "reject" || txt === "rejection") {
                            try { btn.click(); } catch(e) {}
                        }
                    });

                    // Remove fixed elements that block screen interaction
                    var modals = document.querySelectorAll('.engagement-panel-backdrop, .modal-backdrop');
                    modals.forEach(function(m) {
                        if (m) m.style.display = "none";
                    });
                }

                dismissPromo();
                setTimeout(dismissPromo, 400);
                setTimeout(dismissPromo, 1000);
                setTimeout(dismissPromo, 2000);
                setTimeout(dismissPromo, 5000);
            })();
        """.trimIndent()
    }
}

