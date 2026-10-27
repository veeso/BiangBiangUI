package dev.veeso.biangbiangui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import dev.veeso.biangbiangui.services.camera.OcrBox
import dev.veeso.biangbiangui.ui.screens.camera.CameraSessionLogState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CameraSessionLogStateTest {
    @Test
    fun recreationRetainsLogButExitAndProcessRestartDoNot() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        fun owner() = object : ViewModelStoreOwner {
            override val viewModelStore = store
        }
        try {
            val factory = object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return modelClass.cast(CameraSessionLogState { testScheduler.currentTime })!!
                }
            }
            val first = ViewModelProvider(owner(), factory)[CameraSessionLogState::class.java]
            val revision = first.beginSession()
            first.accept(
                listOf(
                    OcrBox("你", "ni", 0, 0, 10, 10),
                    OcrBox("坏", "", 0, 0, 10, 10)
                ),
                revision
            )
            assertEquals(listOf("你"), first.log.entries.map { it.original })
            val originalId = first.log.entries.single().id
            advanceTimeBy(700)
            runCurrent()
            assertEquals(setOf(originalId), first.highlightedIds)
            first.endSession(recreating = true)
            val recreated = ViewModelProvider(owner(), factory)[CameraSessionLogState::class.java]
            assertSame(first, recreated)
            assertEquals(1, recreated.log.entries.size)
            val newRevision = recreated.beginSession()
            recreated.accept(listOf(OcrBox("旧", "old", 0, 0, 10, 10)), revision)
            assertEquals(1, recreated.log.entries.size)
            recreated.accept(emptyList(), newRevision)
            assertEquals(1, recreated.log.entries.size)
            advanceTimeBy(799)
            runCurrent()
            assertEquals(setOf(originalId), recreated.highlightedIds)
            advanceTimeBy(1)
            runCurrent()
            assertTrue(recreated.highlightedIds.isEmpty())
            recreated.endSession(recreating = false)
            assertTrue(recreated.log.entries.isEmpty())
            store.clear()
            val fresh = ViewModelProvider(owner(), factory)[CameraSessionLogState::class.java]
            assertNotSame(recreated, fresh)
            assertTrue(fresh.log.entries.isEmpty())
        } finally {
            store.clear()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun duplicateTicksDoNotExtendIndependentHighlightDeadlines() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val state = CameraSessionLogState { testScheduler.currentTime }
        try {
            val revision = state.beginSession()
            val first = OcrBox("你", "ni", 0, 0, 10, 10)
            state.accept(listOf(first), revision)
            val firstId = state.log.entries.single().id
            advanceTimeBy(1000)
            runCurrent()
            state.accept(listOf(first, OcrBox("好", "hao", 0, 0, 10, 10)), revision)
            val secondId = state.log.entries.first().id
            advanceTimeBy(499)
            runCurrent()
            assertEquals(setOf(firstId, secondId), state.highlightedIds)
            advanceTimeBy(1)
            runCurrent()
            assertEquals(setOf(secondId), state.highlightedIds)
            state.clear()
            assertTrue(state.log.entries.isEmpty())
            assertTrue(state.highlightedIds.isEmpty())
            advanceUntilIdle()
            assertTrue(state.log.entries.isEmpty())
        } finally {
            state.endSession(recreating = false)
            Dispatchers.resetMain()
        }
    }

    @Test
    fun evictedAndClearedHighlightsCannotAffectReinsertedRows() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val state = CameraSessionLogState { testScheduler.currentTime }
        try {
            val revision = state.beginSession()
            val first = OcrBox("row0", "latin0", 0, 0, 10, 10)
            state.accept(listOf(first), revision)
            val evictedId = state.log.entries.single().id
            advanceTimeBy(500)
            runCurrent()
            state.accept((1..50).map { OcrBox("row$it", "latin$it", 0, 0, 10, 10) }, revision)
            assertEquals(50, state.log.entries.size)
            assertEquals(state.log.entries.map { it.id }.toSet(), state.highlightedIds)
            assertTrue(evictedId !in state.highlightedIds)
            state.accept(listOf(first), revision)
            val reinsertedId = state.log.entries.first().id
            assertTrue(reinsertedId != evictedId)
            assertEquals(state.log.entries.map { it.id }.toSet(), state.highlightedIds)
            advanceTimeBy(1000)
            runCurrent()
            assertTrue(reinsertedId in state.highlightedIds)
            state.clear()
            state.accept(listOf(first), revision)
            val freshId = state.log.entries.single().id
            advanceTimeBy(500)
            runCurrent()
            assertEquals(setOf(freshId), state.highlightedIds)
            advanceTimeBy(999)
            runCurrent()
            assertEquals(setOf(freshId), state.highlightedIds)
            advanceTimeBy(1)
            runCurrent()
            assertTrue(state.highlightedIds.isEmpty())
            assertEquals(listOf("row0"), state.log.entries.map { it.original })
        } finally {
            state.endSession(recreating = false)
            Dispatchers.resetMain()
        }
    }

}
