package com.example.uno.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.uno.network.MultiplayerRepository
import com.example.uno.network.RoomInfo
import com.example.uno.network.SocketConnectionStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MultiplayerViewModel(application: Application) : AndroidViewModel(application) {

    val repository = MultiplayerRepository(application.applicationContext)

    val currentRoom: StateFlow<RoomInfo?> = repository.currentRoom
    val connectionStatus: StateFlow<SocketConnectionStatus> = repository.socketManager.connectionStatus

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun createRoom(nickname: String, avatar: String, onSuccess: (String) -> Unit) {
        _isLoading.value = true
        _errorMessage.value = null
        repository.createRoom(nickname, avatar) { success, codeOrErr, _ ->
            _isLoading.value = false
            if (success && codeOrErr != null) {
                onSuccess(codeOrErr)
            } else {
                _errorMessage.value = codeOrErr ?: "Failed to create room"
            }
        }
    }

    fun joinRoom(roomCode: String, nickname: String, avatar: String, onSuccess: () -> Unit) {
        _isLoading.value = true
        _errorMessage.value = null
        repository.joinRoom(roomCode, nickname, avatar) { success, err, _ ->
            _isLoading.value = false
            if (success) {
                onSuccess()
            } else {
                _errorMessage.value = err ?: "Room not found or full"
            }
        }
    }

    fun setReady(isReady: Boolean) {
        repository.setReady(isReady)
    }

    fun startGame(onSuccess: () -> Unit) {
        _isLoading.value = true
        _errorMessage.value = null
        repository.startGame { success, err ->
            _isLoading.value = false
            if (success) {
                onSuccess()
            } else {
                _errorMessage.value = err ?: "Cannot start game"
            }
        }
    }

    fun leaveRoom() {
        repository.leaveRoom()
    }

    fun clearError() {
        _errorMessage.value = null
    }
}
