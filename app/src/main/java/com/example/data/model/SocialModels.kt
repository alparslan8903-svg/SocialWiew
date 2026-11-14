package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class UserPublic(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val username: String = "",
    val bio: String = "",
    val avatarUrl: String = "",
    val isVerified: Boolean = false,
    val followersCount: Long = 0L,
    val followingCount: Long = 0L,
    val videosCount: Long = 0L,
    val isPublic: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class UserPrivate(
    @DocumentId val id: String = "",
    val userId: String = "",
    val email: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class FollowRecord(
    @DocumentId val id: String = "",
    val followerId: String = "",
    val followingId: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class VideoPost(
    @DocumentId val id: String = "",
    val userId: String = "",
    val authorName: String = "",
    val authorUsername: String = "",
    val authorAvatarUrl: String = "",
    val authorVerified: Boolean = false,
    val caption: String = "",
    val videoUrl: String = "",
    val localFileName: String = "",
    val thumbnailData: String = "",
    val chunkCount: Int = 0,
    val durationMs: Long = 0L,
    val tags: List<String> = emptyList(),
    val visibility: String = "public",
    val likesCount: Long = 0L,
    val commentsCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class VideoMediaChunk(
    @DocumentId val id: String = "",
    val videoId: String = "",
    val userId: String = "",
    val chunkIndex: Int = 0,
    val data: String = "",
    val visibility: String = "public",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class VideoLikeRecord(
    @DocumentId val id: String = "",
    val videoId: String = "",
    val userId: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class VideoComment(
    @DocumentId val id: String = "",
    val videoId: String = "",
    val userId: String = "",
    val authorName: String = "",
    val authorUsername: String = "",
    val authorAvatarUrl: String = "",
    val authorVerified: Boolean = false,
    val text: String = "",
    val gifUrl: String = "",
    val gifPreviewUrl: String = "",
    val visibility: String = "public",
    @ServerTimestamp val createdAt: Timestamp? = null
)
