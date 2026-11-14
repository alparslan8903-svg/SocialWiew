package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.UUID

class VibeReelRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun publishVideo_follow_like_and_gifComment_fullFlow() = runBlocking {
        val aliceUid = signInTestUser("alice_${UUID.randomUUID()}@test.com")
        val aliceRepo = SocialRepository(firestore, auth)
        val aliceProfile = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.ensureUserProfile(auth.currentUser).getOrThrow()
        }
        assertEquals(aliceUid, aliceProfile.userId)

        val videoId = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.publishVideo(
                author = aliceProfile,
                caption = "Test reel #${UUID.randomUUID().toString().take(6)}",
                videoUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4",
                localFileName = null,
                thumbnailData = null,
                durationMs = 15000L,
                tags = listOf("vlog", "test")
            ).getOrThrow()
        }
        assertTrue(videoId.isNotBlank())

        val feed = withTimeout(FLOW_TIMEOUT_MS) {
            aliceRepo.observePublicVideos().first { list -> list.any { it.id == videoId } }
        }
        assertTrue(feed.any { it.id == videoId })

        val userVideos = withTimeout(FLOW_TIMEOUT_MS) {
            aliceRepo.observeUserVideos(aliceUid).first { list -> list.any { it.id == videoId } }
        }
        assertTrue(userVideos.any { it.id == videoId && it.userId == aliceUid })

        // Switch to Bob: follow Alice, like Alice's video, post Tenor GIF comment
        val bobUid = signInTestUser("bob_${UUID.randomUUID()}@test.com")
        val bobRepo = SocialRepository(firestore, auth)
        val bobProfile = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.ensureUserProfile(auth.currentUser).getOrThrow()
        }

        withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.toggleFollow(targetUserId = aliceUid, currentlyFollowing = false).getOrThrow()
        }
        val followingIds = withTimeout(FLOW_TIMEOUT_MS) {
            bobRepo.observeFollowingIds(bobUid).first { it.contains(aliceUid) }
        }
        assertTrue(followingIds.contains(aliceUid))

        withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.toggleLike(videoId = videoId, currentlyLiked = false).getOrThrow()
        }
        val likedIds = withTimeout(FLOW_TIMEOUT_MS) {
            bobRepo.observeLikedVideoIds(bobUid).first { it.contains(videoId) }
        }
        assertTrue(likedIds.contains(videoId))

        val commentId = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.addComment(
                videoId = videoId,
                author = bobProfile,
                text = "Harika video!",
                gifUrl = "https://media.tenor.com/demo.gif",
                gifPreviewUrl = "https://media.tenor.com/demo_tiny.gif"
            ).getOrThrow()
        }
        assertTrue(commentId.isNotBlank())

        val comments = withTimeout(FLOW_TIMEOUT_MS) {
            bobRepo.observeComments(videoId).first { list -> list.any { it.id == commentId } }
        }
        assertTrue(comments.any { it.gifUrl == "https://media.tenor.com/demo.gif" })

        // Cross-user negative check: Bob cannot delete Alice's video
        try {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                bobRepo.deleteVideo(videoId).getOrThrow()
            }
            fail("Expected PERMISSION_DENIED when Bob deletes Alice's video")
        } catch (e: Exception) {
            val fsError = e.unwrapFirestoreException()
            assertNotNull("Expected FirebaseFirestoreException, got $e", fsError)
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsError!!.code)
        }
    }

    @Test
    fun unauthenticatedQuery_and_privatePiiRead_failWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser("alice_pii_${UUID.randomUUID()}@test.com")
        val aliceRepo = SocialRepository(firestore, auth)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.ensureUserProfile(auth.currentUser).getOrThrow()
        }

        // Bob cannot read Alice's private PII document
        signInTestUser("bob_pii_${UUID.randomUUID()}@test.com")
        try {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                firestore.collection("users_private").document(aliceUid).get().await()
            }
            fail("Expected PERMISSION_DENIED reading another user's private doc")
        } catch (e: Exception) {
            val fsError = e.unwrapFirestoreException()
            assertNotNull("Expected FirebaseFirestoreException, got $e", fsError)
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsError!!.code)
        }

        // Unauthenticated query fails
        auth.signOut()
        try {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                firestore.collection("videos").whereEqualTo("visibility", "public").get().await()
            }
            fail("Expected PERMISSION_DENIED when unauthenticated")
        } catch (e: Exception) {
            val fsError = e.unwrapFirestoreException()
            assertNotNull("Expected FirebaseFirestoreException, got $e", fsError)
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsError!!.code)
        }
    }

    @Test
    fun cloudVideoChunk_uploadAndDownload_succeedsAcrossUsers() = runBlocking {
        val aliceUid = signInTestUser("alice_chunk_${UUID.randomUUID()}@test.com")
        val aliceRepo = SocialRepository(firestore, auth)
        val aliceProfile = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.ensureUserProfile(auth.currentUser).getOrThrow()
        }

        val tempSource = java.io.File.createTempFile("sample_reel", ".mp4")
        val payloadBytes = "FAKE_MP4_HEADER_AND_FRAMES_${UUID.randomUUID()}".toByteArray()
        tempSource.writeBytes(payloadBytes)

        val videoId = try {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                aliceRepo.publishVideo(
                    author = aliceProfile,
                    caption = "Cloud chunk video",
                    videoUrl = "",
                    localFileName = tempSource.name,
                    localFilePath = tempSource.absolutePath,
                    thumbnailData = null,
                    durationMs = 3000L,
                    tags = listOf("vlog")
                ).getOrThrow()
            }
        } finally {
            tempSource.delete()
        }

        val published = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.getVideoById(videoId)
        }
        assertNotNull(published)
        assertEquals(1, published!!.chunkCount)

        // Bob (another user / device) downloads and reconstructs the video from Firestore chunks
        signInTestUser("bob_chunk_${UUID.randomUUID()}@test.com")
        val bobRepo = SocialRepository(firestore, auth)
        val cachedDest = java.io.File.createTempFile("cached_reel", ".mp4")
        cachedDest.delete()
        try {
            val downloaded = withTimeout(DEFAULT_TIMEOUT_MS) {
                bobRepo.downloadAndCacheCloudVideo(videoId, cachedDest).getOrThrow()
            }
            assertTrue(downloaded.exists())
            assertTrue(payloadBytes.contentEquals(downloaded.readBytes()))
        } finally {
            cachedDest.delete()
        }
    }

    @Test
    fun adminAccount_alparslan_receivesVerifiedBadge_and_regularUserDoesNot() = runBlocking {
        // Regular user does not get verified badge
        signInTestUser("normal_${UUID.randomUUID()}@test.com")
        markCurrentUserEmailVerifiedInEmulator()
        val normalRepo = SocialRepository(firestore, auth)
        val normalProfile = withTimeout(DEFAULT_TIMEOUT_MS) {
            normalRepo.ensureUserProfile(auth.currentUser).getOrThrow()
        }
        org.junit.Assert.assertFalse(normalProfile.isVerified)

        // Official admin email receives isVerified + authorVerified on video & comment
        signInTestUser("alparslan8903@gmail.com")
        markCurrentUserEmailVerifiedInEmulator()
        val adminRepo = SocialRepository(firestore, auth)
        val adminProfile = withTimeout(DEFAULT_TIMEOUT_MS) {
            adminRepo.ensureUserProfile(auth.currentUser).getOrThrow()
        }
        assertTrue(adminProfile.isVerified)

        val adminUid = auth.currentUser!!.uid
        val observedAdminProfile = withTimeout(FLOW_TIMEOUT_MS) {
            adminRepo.observeUserProfile(adminUid).first { it != null }
        }
        assertNotNull(observedAdminProfile)
        assertTrue("Profile observe must deserialize isVerified=true", observedAdminProfile!!.isVerified)

        val observedCreators = withTimeout(FLOW_TIMEOUT_MS) {
            adminRepo.observeCreators().first { list -> list.any { it.userId == adminUid } }
        }
        assertTrue(
            "Creators observe must deserialize isVerified=true",
            observedCreators.first { it.userId == adminUid }.isVerified
        )

        val adminVideoId = withTimeout(DEFAULT_TIMEOUT_MS) {
            adminRepo.publishVideo(
                author = adminProfile,
                caption = "Admin duyurusu",
                videoUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4",
                localFileName = null,
                thumbnailData = null,
                durationMs = 5000L,
                tags = listOf("duyuru")
            ).getOrThrow()
        }
        val publishedAdminVideo = withTimeout(DEFAULT_TIMEOUT_MS) {
            adminRepo.getVideoById(adminVideoId)
        }
        assertNotNull(publishedAdminVideo)
        assertTrue(publishedAdminVideo!!.authorVerified)

        val adminCommentId = withTimeout(DEFAULT_TIMEOUT_MS) {
            adminRepo.addComment(
                videoId = adminVideoId,
                author = adminProfile,
                text = "Hoş geldiniz!"
            ).getOrThrow()
        }
        val comments = withTimeout(FLOW_TIMEOUT_MS) {
            adminRepo.observeComments(adminVideoId).first { list -> list.any { it.id == adminCommentId } }
        }
        assertTrue(comments.first { it.id == adminCommentId }.authorVerified)

        // Verify legacy chunkCount=0 backfill works end-to-end
        val legacyRef = firestore.collection("videos").document()
        val legacyTs = com.google.firebase.firestore.FieldValue.serverTimestamp()
        withTimeout(DEFAULT_TIMEOUT_MS) {
            legacyRef.set(
                mapOf(
                    "userId" to adminUid,
                    "authorName" to adminProfile.displayName,
                    "authorUsername" to adminProfile.username,
                    "authorVerified" to true,
                    "caption" to "Legacy reel",
                    "videoUrl" to "",
                    "localFileName" to "legacy_clip.mp4",
                    "chunkCount" to 0,
                    "durationMs" to 2000L,
                    "tags" to listOf("vlog"),
                    "visibility" to "public",
                    "likesCount" to 0L,
                    "commentsCount" to 0L,
                    "createdAt" to legacyTs,
                    "updatedAt" to legacyTs
                )
            ).await()
        }
        val legacyFile = java.io.File.createTempFile("legacy_backfill", ".mp4")
        val legacyBytes = "LEGACY_VIDEO_BYTES_${UUID.randomUUID()}".toByteArray()
        legacyFile.writeBytes(legacyBytes)
        try {
            val uploadedChunks = withTimeout(DEFAULT_TIMEOUT_MS) {
                adminRepo.backfillVideoChunks(legacyRef.id, legacyFile.absolutePath).getOrThrow()
            }
            assertEquals(1, uploadedChunks)
            val updatedLegacy = withTimeout(DEFAULT_TIMEOUT_MS) {
                adminRepo.getVideoById(legacyRef.id)
            }
            assertEquals(1, updatedLegacy!!.chunkCount)
        } finally {
            legacyFile.delete()
        }
    }

    @Test
    fun verifiedUsername_eymendogan615_and_adminSetUserVerified_succeed() = runBlocking {
        // Regular user starts unverified
        signInTestUser("normal_verify_${UUID.randomUUID()}@test.com")
        markCurrentUserEmailVerifiedInEmulator()
        val normalUid = auth.currentUser!!.uid
        val normalRepo = SocialRepository(firestore, auth)
        val normalProfile = withTimeout(DEFAULT_TIMEOUT_MS) {
            normalRepo.ensureUserProfile(auth.currentUser).getOrThrow()
        }
        org.junit.Assert.assertFalse(normalProfile.isVerified)

        // @eymendogan615 automatically receives blue tick on profile, video & comment
        signInTestUser("eymendogan615@test.com")
        markCurrentUserEmailVerifiedInEmulator()
        val eymenUid = auth.currentUser!!.uid
        val eymenRepo = SocialRepository(firestore, auth)
        val eymenProfile = withTimeout(DEFAULT_TIMEOUT_MS) {
            eymenRepo.ensureUserProfile(auth.currentUser).getOrThrow()
        }
        assertEquals("eymendogan615", eymenProfile.username)
        assertTrue("@eymendogan615 must receive isVerified=true", eymenProfile.isVerified)

        val observedEymen = withTimeout(FLOW_TIMEOUT_MS) {
            eymenRepo.observeUserProfile(eymenUid).first { it != null }
        }
        assertTrue(observedEymen!!.isVerified)

        val eymenVideoId = withTimeout(DEFAULT_TIMEOUT_MS) {
            eymenRepo.publishVideo(
                author = eymenProfile,
                caption = "Eymen video",
                videoUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4",
                localFileName = null,
                thumbnailData = null,
                durationMs = 3000L,
                tags = listOf("vlog")
            ).getOrThrow()
        }
        val publishedEymenVideo = withTimeout(DEFAULT_TIMEOUT_MS) {
            eymenRepo.getVideoById(eymenVideoId)
        }
        assertTrue(publishedEymenVideo!!.authorVerified)

        // Admin grants blue tick to normal user via setUserVerified
        signInTestUser("alparslan8903@gmail.com")
        markCurrentUserEmailVerifiedInEmulator()
        val adminRepo = SocialRepository(firestore, auth)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            adminRepo.ensureUserProfile(auth.currentUser).getOrThrow()
            adminRepo.setUserVerified(normalUid, true).getOrThrow()
        }
        val promotedNormal = withTimeout(FLOW_TIMEOUT_MS) {
            adminRepo.observeUserProfile(normalUid).first { it?.isVerified == true }
        }
        assertTrue(promotedNormal!!.isVerified)
    }

    private companion object {
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 4000L
    }
}
