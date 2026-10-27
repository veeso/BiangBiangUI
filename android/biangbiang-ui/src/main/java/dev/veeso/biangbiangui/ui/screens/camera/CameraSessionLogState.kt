package dev.veeso.biangbiangui.ui.screens.camera

import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.veeso.biangbiangui.services.camera.CameraSessionLog
import dev.veeso.biangbiangui.services.camera.OcrBox
import dev.veeso.biangbiangui.ui.AppDesign
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

/** In-memory OCR log and highlight lifecycle retained across Activity recreation. */
class CameraSessionLogState(
    private val nowMillis: () -> Long = SystemClock::elapsedRealtime,
) : ViewModel() {
    var log by mutableStateOf(CameraSessionLog())
        private set

    var highlightedIds by mutableStateOf<Set<UUID>>(emptySet())
        private set

    private var sessionRevision = 0L
    private val highlightDeadlines = mutableMapOf<UUID, Long>()
    private val highlightJobs = mutableMapOf<UUID, Job>()

    fun beginSession(): Long {
        sessionRevision += 1
        return sessionRevision
    }

    fun isCurrentSession(revision: Long): Boolean = revision == sessionRevision

    fun accept(boxes: List<OcrBox>, revision: Long): Boolean {
        if (revision != sessionRevision) return false

        val eligiblePairs = boxes
            .filter { it.text.isNotBlank() && it.transliteration.isNotBlank() }
            .map { it.text to it.transliteration }
        val updatedLog = log.copy()
        val insertedIds = updatedLog.ingest(eligiblePairs)
        log = updatedLog

        val retainedIds = log.entries.mapTo(mutableSetOf()) { it.id }
        val evictedIds = highlightDeadlines.keys.filterNot(retainedIds::contains)
        evictedIds.forEach { id ->
            highlightDeadlines.remove(id)
            highlightJobs.remove(id)?.cancel()
        }
        highlightedIds = highlightedIds.intersect(retainedIds)

        insertedIds.forEach { id ->
            if (id !in retainedIds) return@forEach
            val deadline = nowMillis() + AppDesign.LOG_HIGHLIGHT_DURATION_MS
            highlightDeadlines[id] = deadline
            highlightedIds = highlightedIds + id
            highlightJobs[id] = viewModelScope.launch {
                delay((deadline - nowMillis()).coerceAtLeast(0L))
                if (highlightDeadlines[id] == deadline) {
                    highlightDeadlines.remove(id)
                    highlightJobs.remove(id)
                    highlightedIds = highlightedIds - id
                }
            }
        }
        return true
    }

    fun endSession(recreating: Boolean) {
        sessionRevision += 1
        if (!recreating) clear()
    }

    fun clear() {
        log = CameraSessionLog()
        highlightJobs.values.forEach(Job::cancel)
        highlightJobs.clear()
        highlightDeadlines.clear()
        highlightedIds = emptySet()
    }
}
