import Foundation
import FirebaseFirestore

struct UserPublic: Identifiable, Hashable {
    var id: String
    var userId: String
    var displayName: String
    var username: String
    var bio: String
    var avatarUrl: String
    var isVerified: Bool
    var followersCount: Int64
    var followingCount: Int64
    var videosCount: Int64
    var isPublic: Bool
    var createdAt: Date?
    var updatedAt: Date?

    init(
        id: String = "",
        userId: String = "",
        displayName: String = "",
        username: String = "",
        bio: String = "",
        avatarUrl: String = "",
        isVerified: Bool = false,
        followersCount: Int64 = 0,
        followingCount: Int64 = 0,
        videosCount: Int64 = 0,
        isPublic: Bool = true,
        createdAt: Date? = nil,
        updatedAt: Date? = nil
    ) {
        self.id = id
        self.userId = userId
        self.displayName = displayName
        self.username = username
        self.bio = bio
        self.avatarUrl = avatarUrl
        self.isVerified = isVerified
        self.followersCount = followersCount
        self.followingCount = followingCount
        self.videosCount = videosCount
        self.isPublic = isPublic
        self.createdAt = createdAt
        self.updatedAt = updatedAt
    }

    init?(document: DocumentSnapshot) {
        guard let data = document.data() else { return nil }
        let username = (data["username"] as? String) ?? ""
        let verified = (data["isVerified"] as? Bool) ??
            (data["verified"] as? Bool) ??
            SocialRepository.isVerifiedUsername(username)

        self.init(
            id: document.documentID,
            userId: (data["userId"] as? String) ?? document.documentID,
            displayName: (data["displayName"] as? String) ?? "",
            username: username,
            bio: (data["bio"] as? String) ?? "",
            avatarUrl: (data["avatarUrl"] as? String) ?? "",
            isVerified: verified || SocialRepository.isVerifiedUsername(username),
            followersCount: (data["followersCount"] as? NSNumber)?.int64Value ?? 0,
            followingCount: (data["followingCount"] as? NSNumber)?.int64Value ?? 0,
            videosCount: (data["videosCount"] as? NSNumber)?.int64Value ?? 0,
            isPublic: (data["isPublic"] as? Bool) ?? true,
            createdAt: (data["createdAt"] as? Timestamp)?.dateValue(),
            updatedAt: (data["updatedAt"] as? Timestamp)?.dateValue()
        )
    }
}

struct VideoPost: Identifiable, Hashable {
    var id: String
    var userId: String
    var authorName: String
    var authorUsername: String
    var authorAvatarUrl: String
    var authorVerified: Bool
    var caption: String
    var videoUrl: String
    var localFileName: String
    var thumbnailData: String
    var chunkCount: Int
    var durationMs: Int64
    var tags: [String]
    var visibility: String
    var likesCount: Int64
    var commentsCount: Int64
    var createdAt: Date?
    var updatedAt: Date?

    init?(document: DocumentSnapshot) {
        guard let data = document.data() else { return nil }
        let username = (data["authorUsername"] as? String) ?? ""
        let verified = ((data["authorVerified"] as? Bool) ?? false) ||
            SocialRepository.isVerifiedUsername(username)

        self.id = document.documentID
        self.userId = (data["userId"] as? String) ?? ""
        self.authorName = (data["authorName"] as? String) ?? ""
        self.authorUsername = username
        self.authorAvatarUrl = (data["authorAvatarUrl"] as? String) ?? ""
        self.authorVerified = verified
        self.caption = (data["caption"] as? String) ?? ""
        self.videoUrl = (data["videoUrl"] as? String) ?? ""
        self.localFileName = (data["localFileName"] as? String) ?? ""
        self.thumbnailData = (data["thumbnailData"] as? String) ?? ""
        self.chunkCount = (data["chunkCount"] as? NSNumber)?.intValue ?? 0
        self.durationMs = (data["durationMs"] as? NSNumber)?.int64Value ?? 0
        self.tags = (data["tags"] as? [String]) ?? []
        self.visibility = (data["visibility"] as? String) ?? "public"
        self.likesCount = (data["likesCount"] as? NSNumber)?.int64Value ?? 0
        self.commentsCount = (data["commentsCount"] as? NSNumber)?.int64Value ?? 0
        self.createdAt = (data["createdAt"] as? Timestamp)?.dateValue()
        self.updatedAt = (data["updatedAt"] as? Timestamp)?.dateValue()
    }
}

struct VideoComment: Identifiable, Hashable {
    var id: String
    var videoId: String
    var userId: String
    var authorName: String
    var authorUsername: String
    var authorAvatarUrl: String
    var authorVerified: Bool
    var text: String
    var gifUrl: String
    var gifPreviewUrl: String
    var visibility: String
    var createdAt: Date?

    init?(document: DocumentSnapshot) {
        guard let data = document.data() else { return nil }
        let username = (data["authorUsername"] as? String) ?? ""
        let verified = ((data["authorVerified"] as? Bool) ?? false) ||
            SocialRepository.isVerifiedUsername(username)

        self.id = document.documentID
        self.videoId = (data["videoId"] as? String) ?? ""
        self.userId = (data["userId"] as? String) ?? ""
        self.authorName = (data["authorName"] as? String) ?? ""
        self.authorUsername = username
        self.authorAvatarUrl = (data["authorAvatarUrl"] as? String) ?? ""
        self.authorVerified = verified
        self.text = (data["text"] as? String) ?? ""
        self.gifUrl = (data["gifUrl"] as? String) ?? ""
        self.gifPreviewUrl = (data["gifPreviewUrl"] as? String) ?? ""
        self.visibility = (data["visibility"] as? String) ?? "public"
        self.createdAt = (data["createdAt"] as? Timestamp)?.dateValue()
    }
}

struct TenorGifItem: Identifiable, Hashable {
    let id: String
    let title: String
    let fullUrl: String
    let previewUrl: String
}

struct AppUpdateInfo: Hashable {
    let repoSlug: String
    let tagName: String
    let title: String
    let releaseNotes: String
    let htmlUrl: String
    let publishedAt: String
    let currentVersion: String
    let isNewerAvailable: Bool
}
