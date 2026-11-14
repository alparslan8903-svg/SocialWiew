package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.UserPublic
import com.example.data.model.VideoPost
import com.example.data.repository.SocialRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

sealed interface ProfileUiState {
    data object Loading : ProfileUiState
    data class Success(
        val profile: UserPublic?,
        val uploadedVideos: List<VideoPost>
    ) : ProfileUiState
    data class Error(val message: String) : ProfileUiState
}

class ProfileViewModel(
    socialRepository: SocialRepository,
    val profileUserId: String
) : ViewModel() {

    val uiState: StateFlow<ProfileUiState> = combine(
        socialRepository.observeUserProfile(profileUserId),
        socialRepository.observeUserVideos(profileUserId)
    ) { profile, videos ->
        ProfileUiState.Success(
            profile = profile,
            uploadedVideos = videos
        ) as ProfileUiState
    }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing profile for $profileUserId", rootError)
            emit(ProfileUiState.Error(rootError.message ?: "Profil verileri yüklenemedi."))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = ProfileUiState.Loading
        )

    private companion object {
        const val TAG = "ProfileVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
