package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.TenorGifItem
import com.example.data.model.UserPublic
import com.example.data.model.VideoComment
import com.example.data.model.VideoPost
import com.example.data.repository.SocialRepository
import com.example.data.repository.TenorRepository
import com.example.data.repository.TenorUiState
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

@OptIn(ExperimentalCoroutinesApi::class)
class SocialFeedViewModel(
    private val socialRepository: SocialRepository,
    private val tenorRepository: TenorRepository,
    val currentUserId: String
) : ViewModel() {

    private val _toastEvents = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val toastEvents: SharedFlow<String> = _toastEvents.asSharedFlow()

    private val _selectedVideoForComments = MutableStateFlow<VideoPost?>(null)
    val selectedVideoForComments: StateFlow<VideoPost?> = _selectedVideoForComments.asStateFlow()

    private val _tenorState = MutableStateFlow<TenorUiState>(TenorUiState.Idle)
    val tenorState: StateFlow<TenorUiState> = _tenorState.asStateFlow()

    private val _isPublishing = MutableStateFlow(false)
    val isPublishing: StateFlow<Boolean> = _isPublishing.asStateFlow()

    init {
        viewModelScope.launch {
            socialRepository.ensureUserProfile().onFailure { e ->
                Log.w(TAG, "Profile bootstrap failed", e)
            }
        }
    }

    val currentUserProfile: StateFlow<UserPublic?> = socialRepository.observeUserProfile(currentUserId)
        .catch { error ->
            unwrapFirestoreOrRethrow(error, "observeUserProfile")
            emit(null)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = null
        )

    val videosState: StateFlow<UiState<List<VideoPost>>> = socialRepository.observePublicVideos()
        .map<List<VideoPost>, UiState<List<VideoPost>>> { UiState.Success(it) }
        .catch { error ->
            val rootError = unwrapFirestoreOrRethrow(error, "observePublicVideos")
            emit(UiState.Error(rootError.message ?: "Videolar yüklenemedi."))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    val creatorsState: StateFlow<UiState<List<UserPublic>>> = socialRepository.observeCreators()
        .map<List<UserPublic>, UiState<List<UserPublic>>> { UiState.Success(it) }
        .catch { error ->
            val rootError = unwrapFirestoreOrRethrow(error, "observeCreators")
            emit(UiState.Error(rootError.message ?: "İçerik üreticileri yüklenemedi."))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    val followingIds: StateFlow<Set<String>> = socialRepository.observeFollowingIds(currentUserId)
        .catch { error ->
            unwrapFirestoreOrRethrow(error, "observeFollowingIds")
            emit(emptySet())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = emptySet()
        )

    val likedVideoIds: StateFlow<Set<String>> = socialRepository.observeLikedVideoIds(currentUserId)
        .catch { error ->
            unwrapFirestoreOrRethrow(error, "observeLikedVideoIds")
            emit(emptySet())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = emptySet()
        )

    val activeCommentsState: StateFlow<UiState<List<VideoComment>>> = _selectedVideoForComments
        .flatMapLatest { video ->
            if (video == null) {
                flowOf<UiState<List<VideoComment>>>(UiState.Success(emptyList()))
            } else {
                socialRepository.observeComments(video.id)
                    .map<List<VideoComment>, UiState<List<VideoComment>>> { UiState.Success(it) }
                    .catch { error ->
                        val rootError = unwrapFirestoreOrRethrow(error, "observeComments")
                        emit(UiState.Error(rootError.message ?: "Yorumlar yüklenemedi."))
                    }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    fun openComments(video: VideoPost) {
        _selectedVideoForComments.value = video
    }

    fun closeComments() {
        _selectedVideoForComments.value = null
    }

    fun loadTenorGifs(query: String) {
        viewModelScope.launch {
            _tenorState.value = TenorUiState.Loading
            _tenorState.value = tenorRepository.loadGifs(query)
        }
    }

    fun toggleLike(video: VideoPost) {
        val currentlyLiked = likedVideoIds.value.contains(video.id)
        viewModelScope.launch {
            socialRepository.toggleLike(video.id, currentlyLiked).onFailure {
                _toastEvents.tryEmit("Beğeni güncellenemedi: ${it.localizedMessage ?: "Hata"}")
            }
        }
    }

    fun toggleFollow(targetUserId: String) {
        if (targetUserId == currentUserId) return
        val currentlyFollowing = followingIds.value.contains(targetUserId)
        viewModelScope.launch {
            socialRepository.toggleFollow(targetUserId, currentlyFollowing).onFailure {
                _toastEvents.tryEmit("Takip işlemi başarısız: ${it.localizedMessage ?: "Hata"}")
            }
        }
    }

    fun publishVideo(
        caption: String,
        videoUrl: String,
        localFileName: String?,
        thumbnailData: String?,
        durationMs: Long,
        tags: List<String>,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            _isPublishing.value = true
            val author = currentUserProfile.value ?: socialRepository.ensureUserProfile().getOrNull()
            if (author == null) {
                _isPublishing.value = false
                _toastEvents.tryEmit("Profil bilgisi alınamadı, lütfen tekrar deneyin.")
                return@launch
            }
            socialRepository.publishVideo(
                author = author,
                caption = caption,
                videoUrl = videoUrl,
                localFileName = localFileName,
                thumbnailData = thumbnailData,
                durationMs = durationMs,
                tags = tags
            ).onSuccess {
                _isPublishing.value = false
                _toastEvents.tryEmit("Videonuz yayınlandı!")
                onSuccess()
            }.onFailure { error ->
                _isPublishing.value = false
                _toastEvents.tryEmit("Video yüklenemedi: ${error.localizedMessage ?: "Hata"}")
            }
        }
    }

    fun deleteVideo(videoId: String) {
        viewModelScope.launch {
            socialRepository.deleteVideo(videoId).onSuccess {
                _toastEvents.tryEmit("Video silindi.")
            }.onFailure { error ->
                _toastEvents.tryEmit("Video silinemedi: ${error.localizedMessage ?: "Hata"}")
            }
        }
    }

    fun addComment(videoId: String, text: String, gif: TenorGifItem? = null) {
        viewModelScope.launch {
            val author = currentUserProfile.value ?: socialRepository.ensureUserProfile().getOrNull()
            if (author == null) {
                _toastEvents.tryEmit("Profil bilgisi alınamadı.")
                return@launch
            }
            socialRepository.addComment(
                videoId = videoId,
                author = author,
                text = text,
                gifUrl = gif?.fullUrl,
                gifPreviewUrl = gif?.previewUrl
            ).onFailure { error ->
                _toastEvents.tryEmit("Yorum gönderilemedi: ${error.localizedMessage ?: "Hata"}")
            }
        }
    }

    fun deleteComment(videoId: String, commentId: String) {
        viewModelScope.launch {
            socialRepository.deleteComment(videoId, commentId).onFailure { error ->
                _toastEvents.tryEmit("Yorum silinemedi: ${error.localizedMessage ?: "Hata"}")
            }
        }
    }

    fun updateProfile(
        displayName: String,
        username: String,
        bio: String,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            socialRepository.updateProfile(displayName, username, bio)
                .onSuccess {
                    _toastEvents.tryEmit("Profil güncellendi.")
                    onSuccess()
                }
                .onFailure { error ->
                    _toastEvents.tryEmit(error.localizedMessage ?: "Profil güncellenemedi.")
                }
        }
    }

    private fun unwrapFirestoreOrRethrow(error: Throwable, operation: String): Throwable {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val rootError = firestoreError ?: error
        Log.w(TAG, "Error in $operation", rootError)
        return rootError
    }

    private companion object {
        const val TAG = "SocialFeedVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
