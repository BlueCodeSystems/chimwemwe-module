package com.bluecodeltd.ecap.chw.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bluecodeltd.ecap.chw.dao.HotspotGroupDao
import com.bluecodeltd.ecap.chw.dao.ParticipantDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDateTime

data class DashboardState(
    val groupsCount: Int = 0,
    val participantsCount: Int = 0,
    val sessionsRecorded: Int = 0,
    val maxSessions: Int = 0,
    val completedCount: Int = 0,
    val lastUpdated: LocalDateTime? = null
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

                _state.postValue(
                    DashboardState(
                        groupsCount = groupsCount,
                        participantsCount = participantsCount,
                        sessionsRecorded = sessionsRecorded,
                        maxSessions = maxSessions,
                        completedCount = completedCount,
                        lastUpdated = LocalDateTime.now()
                    )
                )
            } catch (_: Exception) {
                _state.postValue(DashboardState(lastUpdated = LocalDateTime.now()))
            }
        }
    }
}
