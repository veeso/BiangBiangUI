package dev.veeso.biangbiangui.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import dev.veeso.biangbiangui.config.Branding

// Bridges `Branding.accentColorHex` (a plain string in the Compose-free Config
// layer) to a Compose `Color`. Mirrors iOS `Color+Brand.swift`. This is the
// seam apps use to pass their brand colour: set `Branding.accentColorHex` and
// every config-driven control (active camera buttons, overlay) picks it up.

/**
 * Decodes a `#RGB`, `#RRGGBB`, or `#RRGGBBAA` hex string. Returns `null` when
 * the string is malformed so the caller can fall back to a visible default
 * (never an invisible control).
 */
internal fun brandColorOrNull(hex: String): Color? {
    val raw = hex.trim().removePrefix("#")
    val value = raw.toLongOrNull(16) ?: return null

    return when (raw.length) {
        3 -> { // RGB (12-bit)
            val r = ((value shr 8) and 0xF) / 15.0
            val g = ((value shr 4) and 0xF) / 15.0
            val b = (value and 0xF) / 15.0
            Color(r.toFloat(), g.toFloat(), b.toFloat(), 1f)
        }
        6 -> { // RRGGBB
            val r = ((value shr 16) and 0xFF) / 255.0
            val g = ((value shr 8) and 0xFF) / 255.0
            val b = (value and 0xFF) / 255.0
            Color(r.toFloat(), g.toFloat(), b.toFloat(), 1f)
        }
        8 -> { // RRGGBBAA
            val r = ((value shr 24) and 0xFF) / 255.0
            val g = ((value shr 16) and 0xFF) / 255.0
            val b = ((value shr 8) and 0xFF) / 255.0
            val a = (value and 0xFF) / 255.0
            Color(r.toFloat(), g.toFloat(), b.toFloat(), a.toFloat())
        }
        else -> null
    }
}

/**
 * The brand accent colour, decoded from [Branding.accentColorHex]. Falls back
 * to the Material theme primary when the hex is malformed, mirroring the iOS
 * `.accentColor` fallback.
 */
val Branding.accentColor: Color
    @Composable
    @ReadOnlyComposable
    get() = brandColorOrNull(accentColorHex) ?: MaterialTheme.colorScheme.primary
