package dev.veeso.biangbiangui.services

import android.content.Context
import android.os.LocaleList
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.veeso.biangbiangui.config.LanguageVariant
import dev.veeso.biangbiangui.config.SettingDescriptor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.appSettingsDataStore by preferencesDataStore("biangbiang_settings")

/**
 * DataStore-backed settings store.
 *
 * Ported from the reference BiangBiang Hanzi Android `AppSettingsStore`
 * (`AppSettingsRepository`). The generalisations mirror iOS Phase 2
 * `SettingsStore`:
 * - `AppSettings`/`AppSettingsRepository` -> `SettingsStore`.
 * - The `chineseType` key/accessor is replaced by the opaque
 *   `selectedVariantId` (defaulting to the first supplied variant).
 * - App-supplied extras are persisted under generic `descriptor.<key>` keys,
 *   seeded from [SettingDescriptor.defaultValue] only when absent, exposed via
 *   [value]/[setValue].
 * - History and review keys are kept (history serialised via
 *   [HistorySerializer], dedup via [HistoryStore]); review policy via
 *   [ReviewPromptPolicy], retaining the Android attempts extras.
 */
class SettingsStore(
    private val dataStore: DataStore<Preferences>,
    variants: List<LanguageVariant> = emptyList(),
    private val descriptors: List<SettingDescriptor> = emptyList(),
    private val defaultLanguage: String = LocaleList.getDefault().get(0).language,
) {
    constructor(
        context: Context,
        variants: List<LanguageVariant> = emptyList(),
        descriptors: List<SettingDescriptor> = emptyList(),
        defaultLanguage: String = LocaleList.getDefault().get(0).language,
    ) : this(context.appSettingsDataStore, variants, descriptors, defaultLanguage)

    private val defaultVariantId: String = variants.firstOrNull()?.id ?: ""

    private object Keys {
        val USER_LANGUAGE = stringPreferencesKey("user_language")
        val SELECTED_VARIANT = stringPreferencesKey("selected_variant")
        val HISTORY = stringPreferencesKey("history")
        val REVIEW_LAUNCH_COUNT = intPreferencesKey("review_launch_count")
        val REVIEW_PROMPT_DISMISSED = booleanPreferencesKey("review_prompt_dismissed")
        val REVIEW_ATTEMPTS = intPreferencesKey("review_attempts")
    }

    private fun descriptorKey(key: String) = stringPreferencesKey("descriptor.$key")

    val userLanguage = dataStore.data.map { it[Keys.USER_LANGUAGE] ?: defaultLanguage }

    val selectedVariantId =
        dataStore.data.map { it[Keys.SELECTED_VARIANT] ?: defaultVariantId }

    val history = dataStore.data.map {
        HistorySerializer.fromJson(it[Keys.HISTORY] ?: "")
    }

    val reviewLaunchCount = dataStore.data.map { it[Keys.REVIEW_LAUNCH_COUNT] ?: 0 }

    val reviewPromptDismissed =
        dataStore.data.map { it[Keys.REVIEW_PROMPT_DISMISSED] ?: false }

    val reviewAttempts = dataStore.data.map { it[Keys.REVIEW_ATTEMPTS] ?: 0 }

    suspend fun setUserLanguage(value: String) {
        dataStore.edit { it[Keys.USER_LANGUAGE] = value }
    }

    suspend fun setSelectedVariantId(value: String) {
        dataStore.edit { it[Keys.SELECTED_VARIANT] = value }
    }

    // MARK: - History

    suspend fun addHistory(
        original: String,
        transliteration: String,
        variantId: String,
    ) {
        dataStore.edit { prefs ->
            val current = HistorySerializer.fromJson(prefs[Keys.HISTORY] ?: "")
            val entry = HistoryEntry(
                original = original,
                transliteration = transliteration,
                variantId = variantId,
            )
            prefs[Keys.HISTORY] =
                HistorySerializer.toJson(HistoryStore.insert(entry, current))
        }
    }

    suspend fun deleteHistory(id: String) {
        dataStore.edit { prefs ->
            val current = HistorySerializer.fromJson(prefs[Keys.HISTORY] ?: "")
            prefs[Keys.HISTORY] =
                HistorySerializer.toJson(HistoryStore.delete(id, current))
        }
    }

    suspend fun clearHistory() {
        dataStore.edit { prefs ->
            prefs[Keys.HISTORY] = HistorySerializer.toJson(HistoryStore.clear())
        }
    }

    // MARK: - Review prompt

    /** Increment the cold-launch counter, capped per policy. Call once per cold start. */
    suspend fun registerLaunch() {
        dataStore.edit { prefs ->
            val current = prefs[Keys.REVIEW_LAUNCH_COUNT] ?: 0
            prefs[Keys.REVIEW_LAUNCH_COUNT] =
                ReviewPromptPolicy.nextLaunchCount(current)
        }
    }

    /** "Not now": reset the counter, keep prompting after more launches. */
    suspend fun notNow() {
        dataStore.edit { it[Keys.REVIEW_LAUNCH_COUNT] = 0 }
    }

    /** "Rate now" / "Don't ask again": never show the prompt again. */
    suspend fun dismissForever() {
        dataStore.edit { it[Keys.REVIEW_PROMPT_DISMISSED] = true }
    }

    /** Record a native-flow attempt that did not visibly show; retried later. */
    suspend fun incrementReviewAttempts() {
        dataStore.edit { prefs ->
            prefs[Keys.REVIEW_ATTEMPTS] = (prefs[Keys.REVIEW_ATTEMPTS] ?: 0) + 1
        }
    }

    /** Debug aid: clear dismissed flag and counters so the prompt can re-trigger. */
    suspend fun resetReviewPrompt() {
        dataStore.edit { prefs ->
            prefs[Keys.REVIEW_LAUNCH_COUNT] = 0
            prefs[Keys.REVIEW_PROMPT_DISMISSED] = false
            prefs[Keys.REVIEW_ATTEMPTS] = 0
        }
    }

    // MARK: - Generic descriptor persistence

    /**
     * Seeds descriptor defaults only if absent, so existing user values
     * survive. Mirrors the iOS `SettingsStore.init` seeding pass; call once
     * after construction.
     */
    suspend fun seedDescriptorDefaults() {
        dataStore.edit { prefs ->
            for (descriptor in descriptors) {
                val key = descriptorKey(descriptor.key)
                if (prefs[key] == null) {
                    prefs[key] = descriptor.defaultValue
                }
            }
        }
    }

    suspend fun value(key: String): String? =
        dataStore.data.first()[descriptorKey(key)]

    /** Reactive descriptor value; emits on every persisted change. */
    fun valueFlow(key: String): kotlinx.coroutines.flow.Flow<String?> =
        dataStore.data.map { it[descriptorKey(key)] }

    suspend fun setValue(key: String, value: String) {
        dataStore.edit { it[descriptorKey(key)] = value }
    }
}
