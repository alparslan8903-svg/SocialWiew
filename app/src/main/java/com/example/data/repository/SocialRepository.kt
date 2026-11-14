package com.example.data.repository

import android.content.Context
import android.util.Base64
import com.example.R
import com.example.data.model.FollowRecord
import com.example.data.model.UserPublic
import com.example.data.model.VideoComment
import com.example.data.model.VideoLikeRecord
import com.example.data.model.VideoMediaChunk
import com.example.data.model.VideoPost
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File

class SocialRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("Devam etmek için Google ile giriş yapmalısınız.")
    }

    private fun <T> Flow<T>.withFirestoreErrorLogging(
        operationType: OperationType,
        path: String,
        expectedUid: String? = null
    ): Flow<T> = catch { error ->
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && (expectedUid == null || auth.currentUser?.uid == expectedUid)) {
            handleFirestoreError(exceptionToLog, operationType, path, auth)
        }
        throw error
    }

    suspend fun ensureUserProfile(user: FirebaseUser? = auth.currentUser): Result<UserPublic> = runCatching {
        val current = user ?: throw IllegalStateException("Kullanıcı oturumu bulunamadı.")
        val uid = current.uid
        val publicRef = db.collection("users_public").document(uid)
        val privateRef = db.collection("users_private").document(uid)

        val existingSnap = try {
            publicRef.get().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, publicRef.path, auth)
            throw e
        }

        val isVerifiedAdmin = isCurrentUserAdmin(current)

        if (existingSnap.exists()) {
            val profile = existingSnap.toObject(
                UserPublic::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            )
            if (profile != null) {
                if (isVerifiedAdmin && !profile.isVerified) {
                    val synced = runCatching {
                        publicRef.update(
                            mapOf(
                                "isVerified" to true,
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        ).await()
                    }
                    if (synced.isSuccess) {
                        return@runCatching profile.copy(isVerified = true)
                    }
                }
                return@runCatching profile
            }
        }

        val rawName = current.displayName?.trim().takeUnless { it.isNullOrBlank() } ?: "SocialWiew Kullanıcısı"
        val displayName = rawName.take(50)
        val emailPrefix = current.email?.substringBefore("@")?.lowercase()
            ?.replace(Regex("[^a-z0-9_]"), "")
            ?.take(18)
            ?.takeIf { it.length >= 3 }
        val fallbackHandle = (emailPrefix ?: "viber_${uid.take(6).lowercase()}").padEnd(3, 'x').take(30)
        val avatarUrl = current.photoUrl?.toString()?.take(1000) ?: ""

        val publicPayload = mapOf(
            "userId" to uid,
            "displayName" to displayName,
            "username" to fallbackHandle,
            "bio" to "",
            "avatarUrl" to avatarUrl,
            "isVerified" to isVerifiedAdmin,
            "followersCount" to 0L,
            "followingCount" to 0L,
            "videosCount" to 0L,
            "isPublic" to true,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )

        val privatePayload = mapOf(
            "userId" to uid,
            "email" to (current.email ?: ""),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )

        val batch = db.batch()
        batch.set(publicRef, publicPayload)
        batch.set(privateRef, privatePayload)
        try {
            batch.commit().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, publicRef.path, auth)
            throw e
        }

        UserPublic(
            id = uid,
            userId = uid,
            displayName = displayName,
            username = fallbackHandle,
            bio = "",
            avatarUrl = avatarUrl,
            isVerified = isVerifiedAdmin,
            followersCount = 0L,
            followingCount = 0L,
            videosCount = 0L,
            isPublic = true,
            createdAt = Timestamp.now(),
            updatedAt = Timestamp.now()
        )
    }

    fun observePublicVideos(): Flow<List<VideoPost>> = flow {
        val uid = requireUserId()
        val path = "videos"
        emitAll(
            db.collection(path)
                .whereEqualTo("visibility", "public")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        VideoPost::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .withFirestoreErrorLogging(OperationType.LIST, path, uid)
        )
    }

    fun observeUserVideos(userId: String): Flow<List<VideoPost>> = flow {
        val uid = requireUserId()
        val path = "videos"
        emitAll(
            db.collection(path)
                .whereEqualTo("visibility", "public")
                .whereEqualTo("userId", userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        VideoPost::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .withFirestoreErrorLogging(OperationType.LIST, path, uid)
        )
    }

    fun observeCreators(): Flow<List<UserPublic>> = flow {
        val uid = requireUserId()
        val path = "users_public"
        emitAll(
            db.collection(path)
                .whereEqualTo("isPublic", true)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        UserPublic::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.followersCount }
                }
                .withFirestoreErrorLogging(OperationType.LIST, path, uid)
        )
    }

    fun observeUserProfile(userId: String): Flow<UserPublic?> = flow {
        val uid = requireUserId()
        val path = "users_public/$userId"
        emitAll(
            db.collection("users_public").document(userId)
                .snapshots()
                .map { snapshot ->
                    if (!snapshot.exists()) null
                    else snapshot.toObject(
                        UserPublic::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .withFirestoreErrorLogging(OperationType.GET, path, uid)
        )
    }

    fun observeFollowingIds(userId: String): Flow<Set<String>> = flow {
        val uid = requireUserId()
        val path = "follows"
        emitAll(
            db.collection(path)
                .whereEqualTo("followerId", userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        FollowRecord::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).map { it.followingId }.toSet()
                }
                .withFirestoreErrorLogging(OperationType.LIST, path, uid)
        )
    }

    fun observeLikedVideoIds(userId: String): Flow<Set<String>> = flow {
        val uid = requireUserId()
        val path = "video_likes"
        emitAll(
            db.collection(path)
                .whereEqualTo("userId", userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        VideoLikeRecord::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).map { it.videoId }.toSet()
                }
                .withFirestoreErrorLogging(OperationType.LIST, path, uid)
        )
    }

    fun observeComments(videoId: String): Flow<List<VideoComment>> = flow {
        val uid = requireUserId()
        val path = "videos/$videoId/comments"
        emitAll(
            db.collection("videos").document(videoId).collection("comments")
                .whereEqualTo("videoId", videoId)
                .whereEqualTo("visibility", "public")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        VideoComment::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .withFirestoreErrorLogging(OperationType.LIST, path, uid)
        )
    }

    suspend fun updateProfile(
        displayName: String,
        username: String,
        bio: String
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        val cleanDisplayName = displayName.trim().take(50)
        val cleanUsername = username.trim().lowercase().replace(Regex("[^a-z0-9_.]"), "").take(30)
        require(cleanDisplayName.isNotEmpty()) { "İsim boş olamaz." }
        require(cleanUsername.length >= 3) { "Kullanıcı adı en az 3 karakter olmalıdır." }

        val docRef = db.collection("users_public").document(uid)
        val payload = mapOf(
            "displayName" to cleanDisplayName,
            "username" to cleanUsername,
            "bio" to bio.trim().take(200),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.update(payload).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path, auth)
            throw e
        }
    }

    suspend fun toggleFollow(
        targetUserId: String,
        currentlyFollowing: Boolean
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        require(uid != targetUserId) { "Kendinizi takip edemezsiniz." }

        val followId = "${uid}_${targetUserId}"
        val followRef = db.collection("follows").document(followId)
        val myProfileRef = db.collection("users_public").document(uid)
        val targetProfileRef = db.collection("users_public").document(targetUserId)

        val batch = db.batch()
        if (currentlyFollowing) {
            batch.delete(followRef)
            batch.update(
                myProfileRef,
                mapOf(
                    "followingCount" to FieldValue.increment(-1L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.update(
                targetProfileRef,
                mapOf(
                    "followersCount" to FieldValue.increment(-1L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
        } else {
            batch.set(
                followRef,
                mapOf(
                    "followerId" to uid,
                    "followingId" to targetUserId,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.update(
                myProfileRef,
                mapOf(
                    "followingCount" to FieldValue.increment(1L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.update(
                targetProfileRef,
                mapOf(
                    "followersCount" to FieldValue.increment(1L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
        }

        try {
            batch.commit().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, followRef.path, auth)
            throw e
        }
    }

    suspend fun toggleLike(
        videoId: String,
        currentlyLiked: Boolean
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        val likeId = "${videoId}_${uid}"
        val likeRef = db.collection("video_likes").document(likeId)
        val videoRef = db.collection("videos").document(videoId)

        val batch = db.batch()
        if (currentlyLiked) {
            batch.delete(likeRef)
            batch.update(
                videoRef,
                mapOf(
                    "likesCount" to FieldValue.increment(-1L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
        } else {
            batch.set(
                likeRef,
                mapOf(
                    "videoId" to videoId,
                    "userId" to uid,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.update(
                videoRef,
                mapOf(
                    "likesCount" to FieldValue.increment(1L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
        }

        try {
            batch.commit().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, likeRef.path, auth)
            throw e
        }
    }

    suspend fun publishVideo(
        author: UserPublic,
        caption: String,
        videoUrl: String,
        localFileName: String?,
        localFilePath: String? = null,
        thumbnailData: String?,
        durationMs: Long,
        tags: List<String>
    ): Result<String> = runCatching {
        val uid = requireUserId()
        require(videoUrl.isNotBlank() || !localFileName.isNullOrBlank()) {
            "Lütfen bir video seçin veya geçerli bir video bağlantısı girin."
        }

        val encodedChunks: List<String> = if (!localFilePath.isNullOrBlank()) {
            withContext(Dispatchers.IO) {
                val file = File(localFilePath)
                if (!file.exists() || file.length() <= 0L) {
                    emptyList()
                } else {
                    require(file.length() <= MAX_CLOUD_VIDEO_BYTES) {
                        "Video boyutu en fazla 15 MB olabilir. Lütfen daha kısa bir video seçin."
                    }
                    val bytes = file.readBytes()
                    val chunks = mutableListOf<String>()
                    var offset = 0
                    while (offset < bytes.size) {
                        val end = minOf(offset + CHUNK_RAW_BYTES, bytes.size)
                        val encoded = Base64.encodeToString(bytes, offset, end - offset, Base64.NO_WRAP)
                        chunks.add(encoded)
                        offset = end
                    }
                    require(chunks.size <= MAX_CHUNKS) {
                        "Video parça sınırı aşıldı. Lütfen daha kısa bir video seçin."
                    }
                    chunks
                }
            }
        } else {
            emptyList()
        }

        val videoRef = db.collection("videos").document()
        val profileRef = db.collection("users_public").document(uid)

        val payload = buildMap<String, Any> {
            put("userId", uid)
            put("authorName", author.displayName.ifBlank { "Kullanıcı" }.take(60))
            put("authorUsername", author.username.ifBlank { "viber" }.take(40))
            if (author.avatarUrl.isNotBlank()) put("authorAvatarUrl", author.avatarUrl.take(1000))
            put("authorVerified", author.isVerified || isCurrentUserAdmin(auth.currentUser))
            put("caption", caption.trim().take(300))
            put("videoUrl", videoUrl.trim().take(2000))
            if (!localFileName.isNullOrBlank()) put("localFileName", localFileName.take(200))
            if (!thumbnailData.isNullOrBlank()) put("thumbnailData", thumbnailData.take(150000))
            put("chunkCount", encodedChunks.size)
            put("durationMs", durationMs.coerceAtLeast(0L))
            put("tags", tags.map { it.trim().take(24) }.filter { it.isNotEmpty() }.take(5))
            put("visibility", "public")
            put("likesCount", 0L)
            put("commentsCount", 0L)
            put("createdAt", FieldValue.serverTimestamp())
            put("updatedAt", FieldValue.serverTimestamp())
        }

        val firstChunkBatchCount = minOf(encodedChunks.size, CHUNKS_PER_BATCH)
        val batch = db.batch()
        batch.set(videoRef, payload)
        batch.update(
            profileRef,
            mapOf(
                "videosCount" to FieldValue.increment(1L),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        for (idx in 0 until firstChunkBatchCount) {
            val chunkRef = videoRef.collection("media_chunks").document("chunk_$idx")
            batch.set(
                chunkRef,
                mapOf(
                    "videoId" to videoRef.id,
                    "userId" to uid,
                    "chunkIndex" to idx,
                    "data" to encodedChunks[idx],
                    "visibility" to "public",
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
        }

        try {
            batch.commit().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, videoRef.path, auth)
            throw e
        }

        var idx = firstChunkBatchCount
        while (idx < encodedChunks.size) {
            val end = minOf(idx + CHUNKS_PER_BATCH, encodedChunks.size)
            val nextBatch = db.batch()
            for (c in idx until end) {
                val chunkRef = videoRef.collection("media_chunks").document("chunk_$c")
                nextBatch.set(
                    chunkRef,
                    mapOf(
                        "videoId" to videoRef.id,
                        "userId" to uid,
                        "chunkIndex" to c,
                        "data" to encodedChunks[c],
                        "visibility" to "public",
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            try {
                nextBatch.commit().await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.CREATE, "${videoRef.path}/media_chunks", auth)
                throw e
            }
            idx = end
        }

        videoRef.id
    }

    suspend fun downloadAndCacheCloudVideo(videoId: String, destFile: File): Result<File> = runCatching {
        requireUserId()
        if (destFile.exists() && destFile.length() > 0L) {
            return@runCatching destFile
        }
        val path = "videos/$videoId/media_chunks"
        val snapshot = try {
            db.collection("videos")
                .document(videoId)
                .collection("media_chunks")
                .whereEqualTo("videoId", videoId)
                .whereEqualTo("visibility", "public")
                .get()
                .await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, path, auth)
            throw e
        }

        val chunks = snapshot.toObjects(
            VideoMediaChunk::class.java,
            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
        ).sortedBy { it.chunkIndex }

        require(chunks.isNotEmpty()) { "Video bulut parçaları bulunamadı." }

        withContext(Dispatchers.IO) {
            destFile.parentFile?.mkdirs()
            val tempFile = File(destFile.parentFile, "${destFile.name}.tmp")
            tempFile.outputStream().use { out ->
                for (chunk in chunks) {
                    val decoded = Base64.decode(chunk.data, Base64.DEFAULT)
                    out.write(decoded)
                }
            }
            if (destFile.exists()) destFile.delete()
            check(tempFile.renameTo(destFile)) { "Video önbelleğe yazılamadı." }
            destFile
        }
    }

    suspend fun deleteVideo(videoId: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        val videoRef = db.collection("videos").document(videoId)
        val profileRef = db.collection("users_public").document(uid)

        val batch = db.batch()
        batch.delete(videoRef)
        batch.update(
            profileRef,
            mapOf(
                "videosCount" to FieldValue.increment(-1L),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )

        try {
            batch.commit().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, videoRef.path, auth)
            throw e
        }
    }

    suspend fun addComment(
        videoId: String,
        author: UserPublic,
        text: String,
        gifUrl: String? = null,
        gifPreviewUrl: String? = null
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val cleanText = text.trim().take(500)
        val cleanGifUrl = gifUrl?.trim()?.take(1000)
        require(cleanText.isNotEmpty() || !cleanGifUrl.isNullOrEmpty()) {
            "Yorum metni veya GIF seçmelisiniz."
        }

        val videoRef = db.collection("videos").document(videoId)
        val commentRef = videoRef.collection("comments").document()

        val payload = buildMap<String, Any> {
            put("videoId", videoId)
            put("userId", uid)
            put("authorName", author.displayName.ifBlank { "Kullanıcı" }.take(60))
            put("authorUsername", author.username.ifBlank { "viber" }.take(40))
            if (author.avatarUrl.isNotBlank()) put("authorAvatarUrl", author.avatarUrl.take(1000))
            put("authorVerified", author.isVerified || isCurrentUserAdmin(auth.currentUser))
            put("text", cleanText)
            if (!cleanGifUrl.isNullOrEmpty()) put("gifUrl", cleanGifUrl)
            if (!gifPreviewUrl.isNullOrBlank()) put("gifPreviewUrl", gifPreviewUrl.trim().take(1000))
            put("visibility", "public")
            put("createdAt", FieldValue.serverTimestamp())
        }

        val batch = db.batch()
        batch.set(commentRef, payload)
        batch.update(
            videoRef,
            mapOf(
                "commentsCount" to FieldValue.increment(1L),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )

        try {
            batch.commit().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, commentRef.path, auth)
            throw e
        }

        commentRef.id
    }

    suspend fun deleteComment(videoId: String, commentId: String): Result<Unit> = runCatching {
        requireUserId()
        val videoRef = db.collection("videos").document(videoId)
        val commentRef = videoRef.collection("comments").document(commentId)

        val batch = db.batch()
        batch.delete(commentRef)
        batch.update(
            videoRef,
            mapOf(
                "commentsCount" to FieldValue.increment(-1L),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )

        try {
            batch.commit().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, commentRef.path, auth)
            throw e
        }
    }

    suspend fun getVideoById(videoId: String): VideoPost? {
        val docRef = db.collection("videos").document(videoId)
        return try {
            docRef.get().await().toObject(
                VideoPost::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path, auth)
            throw e
        }
    }

    private fun isCurrentUserAdmin(user: FirebaseUser?): Boolean {
        return user?.email?.trim()?.equals(ADMIN_EMAIL, ignoreCase = true) == true
    }

    private companion object {
        const val ADMIN_EMAIL = "alparslan8903@gmail.com"
        const val MAX_CLOUD_VIDEO_BYTES = 15_500_000L
        const val CHUNK_RAW_BYTES = 450_000
        const val MAX_CHUNKS = 36
        const val CHUNKS_PER_BATCH = 6
    }
}
