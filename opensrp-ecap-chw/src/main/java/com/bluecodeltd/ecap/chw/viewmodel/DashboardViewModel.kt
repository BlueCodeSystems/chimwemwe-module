package com.bluecodeltd.chimwemwe.chw.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bluecodeltd.chimwemwe.chw.dao.HotspotGroupDao
import com.bluecodeltd.chimwemwe.chw.dao.ParticipantDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.util.LinkedHashMap

data class DashboardState(
    val groupsCount: Int = 0,
    val participantsCount: Int = 0,
    val sessionsRecorded: Int = 0,
    val maxSessions: Int = 0,
    val completedCount: Int = 0,
    val lastUpdated: LocalDateTime? = null,
    val facilityCounts: LinkedHashMap<String, Int> = LinkedHashMap(),
    val facilitiesCount: Int = 0,
    val hotspotsCount: Int = 0
)

class DashboardViewModel : ViewModel() {
    private val _state = MutableLiveData(DashboardState())
    val state: LiveData<DashboardState> = _state

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val groups = HotspotGroupDao.getAllGroups() ?: emptyList()
                val groupsCount = groups.size
                val participantsCount = groups.sumOf { it.participantCount }
                val sessionsRecorded = groups.sumOf { it.sessionsRecorded }
                val maxSessions = groupsCount * 14
                val completedCount = try { ParticipantDao.countCompletedParticipants() } catch (_: Exception) { 0 }

                // Compute facility counts (case-insensitive key dedup, preserve first-occurrence casing)
                val rawCounts = mutableMapOf<String, Int>()   // lowercase key → count
                val keyToLabel = mutableMapOf<String, String>() // lowercase key → display label
                for (group in groups) {
                    val raw = group.getNearestHealthFacility() ?: continue
                    val trimmed = raw.trim()
                    if (trimmed.isBlank()) continue
                    val lower = trimmed.lowercase()
                    if (!keyToLabel.containsKey(lower)) keyToLabel[lower] = trimmed
                    rawCounts[lower] = (rawCounts[lower] ?: 0) + 1
                }
                val facilityCounts = rawCounts.entries
                    .sortedByDescending { it.value }
                    .fold(LinkedHashMap<String, Int>()) { map, entry ->
                        map[keyToLabel[entry.key]!!] = entry.value
                        map
                    }

                val facilitiesCount = facilityCounts.size
                val hotspotsCount = groups
                    .mapNotNull { it.hotspotName?.trim()?.lowercase()?.takeIf { s -> s.isNotBlank() } }
                    .toSet().size

                _state.postValue(
                    DashboardState(
                        groupsCount = groupsCount,
                        participantsCount = participantsCount,
                        sessionsRecorded = sessionsRecorded,
                        maxSessions = maxSessions,
                        completedCount = completedCount,
                        lastUpdated = LocalDateTime.now(),
                        facilityCounts = facilityCounts,
                        facilitiesCount = facilitiesCount,
                        hotspotsCount = hotspotsCount
                    )
                )
            } catch (_: Exception) {
                _state.postValue(DashboardState(lastUpdated = LocalDateTime.now()))
            }
        }
    }
}
