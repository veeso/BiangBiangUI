package dev.veeso.biangbiangui.services

import java.util.UUID

/**
 * A single transliteration entry persisted in history.
 *
 * Mirrors iOS `HistoryEntry`. The reference Android app keyed entries by a
 * `HistoryVariant` enum (MANDARIN/CANTONESE); the library generalises this to
 * an opaque [variantId] string so any [dev.veeso.biangbiangui.config.LanguageVariant]
 * can be recorded without an app-specific enum.
 */
data class HistoryEntry(
    val id: String = UUID.randomUUID().toString(),
    val original: String,
    val transliteration: String,
    val variantId: String,
    val timestamp: Long = System.currentTimeMillis(),
)
