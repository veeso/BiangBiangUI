package dev.veeso.biangbiangui

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import dev.veeso.biangbiangui.config.SettingDescriptor
import dev.veeso.biangbiangui.services.SettingsStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.UUID

/**
 * Mirrors iOS Phase 2 `SettingsStoreTests`. DataStore requires an Android
 * context, so this runs under Robolectric with a fresh on-disk DataStore per
 * test (the iOS tests use a fresh `UserDefaults` suite per test).
 */
@RunWith(RobolectricTestRunner::class)
class SettingsStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun freshDataStore(): DataStore<Preferences> =
        PreferenceDataStoreFactory.create {
            tmp.newFile("test_${UUID.randomUUID()}.preferences_pb")
        }

    // MARK: - History

    @Test
    fun defaultHistoryIsEmpty() = runTest {
        val s = SettingsStore(freshDataStore())
        assertTrue(s.history.first().isEmpty())
    }

    @Test
    fun addHistoryDedupsConsecutive() = runTest {
        val s = SettingsStore(freshDataStore())
        s.addHistory("你好", "nǐ hǎo", "mandarin")
        s.addHistory("你好", "nǐ hǎo", "mandarin")
        assertEquals(1, s.history.first().size)
    }

    @Test
    fun historyRoundTripsThroughDataStore() = runTest {
        val ds = freshDataStore()
        SettingsStore(ds).addHistory("我", "wǒ", "cantonese")
        val b = SettingsStore(ds)
        val list = b.history.first()
        assertEquals(1, list.size)
        assertEquals("我", list.first().original)
        assertEquals("cantonese", list.first().variantId)
    }

    @Test
    fun clearHistoryEmptiesAndPersists() = runTest {
        val ds = freshDataStore()
        val a = SettingsStore(ds)
        a.addHistory("我", "wǒ", "mandarin")
        a.clearHistory()
        assertTrue(SettingsStore(ds).history.first().isEmpty())
    }

    // MARK: - Review prompt

    @Test
    fun defaultsAreZeroAndNotDismissed() = runTest {
        val s = SettingsStore(freshDataStore())
        assertEquals(0, s.reviewLaunchCount.first())
        assertFalse(s.reviewPromptDismissed.first())
    }

    @Test
    fun registerLaunchIncrementsAndCapsAtFive() = runTest {
        val s = SettingsStore(freshDataStore())
        repeat(7) { s.registerLaunch() }
        assertEquals(5, s.reviewLaunchCount.first())
    }

    @Test
    fun registerLaunchPersists() = runTest {
        val ds = freshDataStore()
        val a = SettingsStore(ds)
        a.registerLaunch()
        a.registerLaunch()
        assertEquals(2, SettingsStore(ds).reviewLaunchCount.first())
    }

    @Test
    fun notNowResetsCountKeepsDismissedFalse() = runTest {
        val s = SettingsStore(freshDataStore())
        s.registerLaunch(); s.registerLaunch(); s.registerLaunch()
        s.notNow()
        assertEquals(0, s.reviewLaunchCount.first())
        assertFalse(s.reviewPromptDismissed.first())
    }

    @Test
    fun dismissForeverPersists() = runTest {
        val ds = freshDataStore()
        val a = SettingsStore(ds)
        a.dismissForever()
        assertTrue(a.reviewPromptDismissed.first())
        assertTrue(SettingsStore(ds).reviewPromptDismissed.first())
    }

    // MARK: - Descriptors / variant

    @Test
    fun descriptorRoundTripsAndSeedsDefault() = runTest {
        val d = SettingDescriptor(
            key = "quranMode",
            kind = SettingDescriptor.Kind.Toggle,
            label = "Quran",
            defaultValue = "false",
        )
        val s = SettingsStore(freshDataStore(), descriptors = listOf(d))
        s.seedDescriptorDefaults()
        assertEquals("false", s.value("quranMode"))
        s.setValue("quranMode", "true")
        assertEquals("true", s.value("quranMode"))
    }

    @Test
    fun selectedVariantPersists() = runTest {
        val ds = freshDataStore()
        SettingsStore(ds).setSelectedVariantId("cantonese")
        assertEquals("cantonese", SettingsStore(ds).selectedVariantId.first())
    }
}
