import Foundation
import FirebaseAuth
import FirebaseFirestore

enum FirestoreOperationType: String {
    case create
    case update
    case delete
    case list
    case get
    case write
}

final class SocialRepository {
    static let adminEmail = "alparslan8903@gmail.com"
    private static let verifiedUsernames: Set<String> = ["alparslan8903", "eymendogan615"]
    private static let maxCloudVideoBytes = 15_500_000
    private static let chunkRawBytes = 450_000
    private static let maxChunks = 36
    private static let chunksPerBatch = 6

    let db: Firestore
    let auth: Auth

    init(databaseId: String = SocialRepository.loadConfiguredDatabaseId(), auth: Auth = Auth.auth()) {
        self.db = Firestore.firestore(database: databaseId)
        self.auth = auth
    }

    static func loadConfiguredDatabaseId() -> String {
        if let url = Bundle.main.url(forResource: "FirebaseAppletConfig", withExtension: "plist"),
           let dict = NSDictionary(contentsOf: url) as? [String: Any],
           let dbId = dict["FirestoreDatabaseId"] as? String,
           !dbId.isEmpty {
            return dbId
        }
        return "ai-studio-socialwiew-76843402-c996-4e5f-82f3-b5c50f47323a"
    }

    static func isVerifiedUsername(_ username: String?) -> Bool {
        let clean = (username ?? "")
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .replacingOccurrences(of: "@", with: "")
            .lowercased()
        return verifiedUsernames.contains(clean)
    }

    static func isCurrentUserAdmin(_ user: User?) -> Bool {
        user?.email?.trimmingCharacters(in: .whitespacesAndNewlines).lowercased() == adminEmail
    }

    private func requireUserId() throws -> String {
        guard let uid = auth.currentUser?.uid else {
            throw NSError(domain: "SocialRepository", code: 401, userInfo: [
                NSLocalizedDescriptionKey: "Devam etmek için Google ile giriş yapmalısınız."
            ])
        }
        return uid
    }

    private func logAndWrapFirestoreError(_ error: Error, operation: FirestoreOperationType, path: String) -> Error {
        let user = auth.currentUser
        let providerInfo: [[String: String]] = (user?.providerData ?? []).map { info in
            [
                "providerId": info.providerID,
                "email": info.email ?? ""
            ]
        }
        let payload: [String: Any] = [
            "error": error.localizedDescription,
            "operationType": operation.rawValue,
            "path": path,
            "authInfo": [
                "userId": user?.uid ?? "",
                "email": user?.email ?? "",
                "emailVerified": user?.isEmailVerified ?? false,
                "isAnonymous": user?.isAnonymous ?? false,
                "providerInfo": providerInfo
            ]
        ]
        if let data = try? JSONSerialization.data(withJSONObject: payload),
           let json = String(data: data, encoding: .utf8) {
            print("Firestore Error: \(json)")
            return NSError(domain: "FirestoreErrorInfo", code: -1, userInfo: [NSLocalizedDescriptionKey: json])
        }
        return error
    }

    func testConnection() async {
        do {
            _ = try await db.collection("test").document("connection").getDocument(source: .server)
        } catch {
            print("Firestore connection check: \(error.localizedDescription)")
        }
    }

    func ensureUserProfile() async throws -> UserPublic {
        guard let current = auth.currentUser else {
            throw NSError(domain: "SocialRepository", code: 401, userInfo: [
                NSLocalizedDescriptionKey: "Kullanıcı oturumu bulunamadı."
            ])
        }
        let uid = current.uid
        let publicRef = db.collection("users_public").document(uid)
        let privateRef = db.collection("users_private").document(uid)

        let existingSnap: DocumentSnapshot
        do {
            existingSnap = try await publicRef.getDocument()
        } catch {
            throw logAndWrapFirestoreError(error, operation: .get, path: publicRef.path)
        }

        let isAdmin = Self.isCurrentUserAdmin(current)
        if existingSnap.exists, var profile = UserPublic(document: existingSnap) {
            let shouldVerify = isAdmin || Self.isVerifiedUsername(profile.username)
            if shouldVerify && !profile.isVerified {
                try? await publicRef.updateData([
                    "isVerified": true,
                    "updatedAt": FieldValue.serverTimestamp()
                ])
                profile.isVerified = true
            }
            return profile
        }

        let rawName = current.displayName?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        let displayName = String((rawName.isEmpty ? "SocialWiew Kullanıcısı" : rawName).prefix(50))
        let emailPrefixRaw = current.email?.split(separator: "@").first.map(String.init)?.lowercased() ?? ""
        let emailPrefix = emailPrefixRaw.replacingOccurrences(of: "[^a-z0-9_]", with: "", options: .regularExpression)
        var handle = emailPrefix.count >= 3 ? String(emailPrefix.prefix(18)) : "viber_\(String(uid.prefix(6)).lowercased())"
        while handle.count < 3 { handle.append("x") }
        handle = String(handle.prefix(30))

        let avatarUrl = String((current.photoURL?.absoluteString ?? "").prefix(1000))
        let initialVerified = isAdmin || Self.isVerifiedUsername(handle)

        let publicPayload: [String: Any] = [
            "userId": uid,
            "displayName": displayName,
            "username": handle,
            "bio": "",
            "avatarUrl": avatarUrl,
            "isVerified": initialVerified,
            "followersCount": Int64(0),
            "followingCount": Int64(0),
            "videosCount": Int64(0),
            "isPublic": true,
            "createdAt": FieldValue.serverTimestamp(),
            "updatedAt": FieldValue.serverTimestamp()
        ]

        let privatePayload: [String: Any] = [
            "userId": uid,
            "email": String((current.email ?? "").prefix(200)),
            "createdAt": FieldValue.serverTimestamp(),
            "updatedAt": FieldValue.serverTimestamp()
        ]

        let batch = db.batch()
        batch.setData(publicPayload, forDocument: publicRef)
        batch.setData(privatePayload, forDocument: privateRef)
        do {
            try await batch.commit()
        } catch {
            throw logAndWrapFirestoreError(error, operation: .create, path: publicRef.path)
        }

        return UserPublic(
            id: uid,
            userId: uid,
            displayName: displayName,
            username: handle,
            bio: "",
            avatarUrl: avatarUrl,
            isVerified: initialVerified,
            followersCount: 0,
            followingCount: 0,
            videosCount: 0,
            isPublic: true,
            createdAt: Date(),
            updatedAt: Date()
        )
    }

    func observePublicVideos(onUpdate: @escaping ([VideoPost]) -> Void, onError: @escaping (Error) -> Void) -> ListenerRegistration {
        db.collection("videos")
            .whereField("visibility", isEqualTo: "public")
            .addSnapshotListener { [weak self] snapshot, error in
                if let error {
                    onError(self?.logAndWrapFirestoreError(error, operation: .list, path: "videos") ?? error)
                    return
                }
                let list = (snapshot?.documents ?? [])
                    .compactMap(VideoPost.init(document:))
                    .sorted { ($0.createdAt ?? .distantPast) > ($1.createdAt ?? .distantPast) }
                onUpdate(list)
            }
    }

    func observeCreators(onUpdate: @escaping ([UserPublic]) -> Void, onError: @escaping (Error) -> Void) -> ListenerRegistration {
        db.collection("users_public")
            .whereField("isPublic", isEqualTo: true)
            .addSnapshotListener { [weak self] snapshot, error in
                if let error {
                    onError(self?.logAndWrapFirestoreError(error, operation: .list, path: "users_public") ?? error)
                    return
                }
                let list = (snapshot?.documents ?? [])
                    .compactMap(UserPublic.init(document:))
                    .sorted { $0.followersCount > $1.followersCount }
                onUpdate(list)
            }
    }

    func observeFollowingIds(userId: String, onUpdate: @escaping (Set<String>) -> Void) -> ListenerRegistration {
        db.collection("follows")
            .whereField("followerId", isEqualTo: userId)
            .addSnapshotListener { snapshot, _ in
                let ids = Set((snapshot?.documents ?? []).compactMap { $0.data()["followingId"] as? String })
                onUpdate(ids)
            }
    }

    func observeLikedVideoIds(userId: String, onUpdate: @escaping (Set<String>) -> Void) -> ListenerRegistration {
        db.collection("video_likes")
            .whereField("userId", isEqualTo: userId)
            .addSnapshotListener { snapshot, _ in
                let ids = Set((snapshot?.documents ?? []).compactMap { $0.data()["videoId"] as? String })
                onUpdate(ids)
            }
    }

    func observeComments(videoId: String, onUpdate: @escaping ([VideoComment]) -> Void) -> ListenerRegistration {
        db.collection("videos").document(videoId).collection("comments")
            .whereField("videoId", isEqualTo: videoId)
            .whereField("visibility", isEqualTo: "public")
            .addSnapshotListener { snapshot, _ in
                let list = (snapshot?.documents ?? [])
                    .compactMap(VideoComment.init(document:))
                    .sorted { ($0.createdAt ?? .distantPast) > ($1.createdAt ?? .distantPast) }
                onUpdate(list)
            }
    }

    func observeSharedUpdateRepo(onUpdate: @escaping (String) -> Void) -> ListenerRegistration {
        db.collection("app_config").document("updates")
            .addSnapshotListener { snapshot, _ in
                let repo = (snapshot?.data()?["githubRepo"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
                onUpdate(repo)
            }
    }

    func updateProfile(displayName: String, username: String, bio: String) async throws {
        let uid = try requireUserId()
        let cleanDisplay = String(displayName.trimmingCharacters(in: .whitespacesAndNewlines).prefix(50))
        let cleanUser = String(
            username.trimmingCharacters(in: .whitespacesAndNewlines)
                .lowercased()
                .replacingOccurrences(of: "[^a-z0-9_.]", with: "", options: .regularExpression)
                .prefix(30)
        )
        guard !cleanDisplay.isEmpty else {
            throw NSError(domain: "SocialRepository", code: 400, userInfo: [NSLocalizedDescriptionKey: "İsim boş olamaz."])
        }
        guard cleanUser.count >= 3 else {
            throw NSError(domain: "SocialRepository", code: 400, userInfo: [NSLocalizedDescriptionKey: "Kullanıcı adı en az 3 karakter olmalıdır."])
        }
        let docRef = db.collection("users_public").document(uid)
        do {
            try await docRef.updateData([
                "displayName": cleanDisplay,
                "username": cleanUser,
                "bio": String(bio.trimmingCharacters(in: .whitespacesAndNewlines).prefix(200)),
                "updatedAt": FieldValue.serverTimestamp()
            ])
        } catch {
            throw logAndWrapFirestoreError(error, operation: .update, path: docRef.path)
        }
    }

    func setUserVerified(targetUserId: String, verified: Bool) async throws {
        _ = try requireUserId()
        guard Self.isCurrentUserAdmin(auth.currentUser) else {
            throw NSError(domain: "SocialRepository", code: 403, userInfo: [
                NSLocalizedDescriptionKey: "Yalnızca yetkili admin mavi tik verebilir."
            ])
        }
        let docRef = db.collection("users_public").document(targetUserId)
        do {
            try await docRef.updateData([
                "isVerified": verified,
                "updatedAt": FieldValue.serverTimestamp()
            ])
        } catch {
            throw logAndWrapFirestoreError(error, operation: .update, path: docRef.path)
        }
    }

    func saveSharedUpdateRepo(_ repoSlug: String) async throws {
        let uid = try requireUserId()
        guard Self.isCurrentUserAdmin(auth.currentUser) else {
            throw NSError(domain: "SocialRepository", code: 403, userInfo: [
                NSLocalizedDescriptionKey: "Genel güncelleme deposunu yalnızca admin güncelleyebilir."
            ])
        }
        let docRef = db.collection("app_config").document("updates")
        do {
            try await docRef.setData([
                "githubRepo": String(repoSlug.trimmingCharacters(in: .whitespacesAndNewlines).prefix(160)),
                "updatedBy": uid,
                "updatedAt": FieldValue.serverTimestamp()
            ])
        } catch {
            throw logAndWrapFirestoreError(error, operation: .write, path: docRef.path)
        }
    }

    func toggleFollow(targetUserId: String, currentlyFollowing: Bool) async throws {
        let uid = try requireUserId()
        guard uid != targetUserId else { return }
        let followRef = db.collection("follows").document("\(uid)_\(targetUserId)")
        let myRef = db.collection("users_public").document(uid)
        let targetRef = db.collection("users_public").document(targetUserId)

        let batch = db.batch()
        if currentlyFollowing {
            batch.deleteDocument(followRef)
            batch.updateData(["followingCount": FieldValue.increment(Int64(-1)), "updatedAt": FieldValue.serverTimestamp()], forDocument: myRef)
            batch.updateData(["followersCount": FieldValue.increment(Int64(-1)), "updatedAt": FieldValue.serverTimestamp()], forDocument: targetRef)
        } else {
            batch.setData([
                "followerId": uid,
                "followingId": targetUserId,
                "createdAt": FieldValue.serverTimestamp()
            ], forDocument: followRef)
            batch.updateData(["followingCount": FieldValue.increment(Int64(1)), "updatedAt": FieldValue.serverTimestamp()], forDocument: myRef)
            batch.updateData(["followersCount": FieldValue.increment(Int64(1)), "updatedAt": FieldValue.serverTimestamp()], forDocument: targetRef)
        }
        do {
            try await batch.commit()
        } catch {
            throw logAndWrapFirestoreError(error, operation: .write, path: followRef.path)
        }
    }

    func toggleLike(videoId: String, currentlyLiked: Bool) async throws {
        let uid = try requireUserId()
        let likeRef = db.collection("video_likes").document("\(videoId)_\(uid)")
        let videoRef = db.collection("videos").document(videoId)

        let batch = db.batch()
        if currentlyLiked {
            batch.deleteDocument(likeRef)
            batch.updateData(["likesCount": FieldValue.increment(Int64(-1)), "updatedAt": FieldValue.serverTimestamp()], forDocument: videoRef)
        } else {
            batch.setData([
                "videoId": videoId,
                "userId": uid,
                "createdAt": FieldValue.serverTimestamp()
            ], forDocument: likeRef)
            batch.updateData(["likesCount": FieldValue.increment(Int64(1)), "updatedAt": FieldValue.serverTimestamp()], forDocument: videoRef)
        }
        do {
            try await batch.commit()
        } catch {
            throw logAndWrapFirestoreError(error, operation: .write, path: likeRef.path)
        }
    }

    func publishVideo(
        author: UserPublic,
        caption: String,
        videoUrl: String,
        localFileData: Data?,
        localFileName: String?,
        thumbnailData: String?,
        durationMs: Int64,
        tags: [String]
    ) async throws -> String {
        let uid = try requireUserId()
        let cleanUrl = String(videoUrl.trimmingCharacters(in: .whitespacesAndNewlines).prefix(2000))

        var encodedChunks: [String] = []
        if let fileData = localFileData, !fileData.isEmpty {
            guard fileData.count <= Self.maxCloudVideoBytes else {
                throw NSError(domain: "SocialRepository", code: 400, userInfo: [
                    NSLocalizedDescriptionKey: "Video boyutu en fazla 15 MB olabilir. Lütfen daha kısa bir video seçin."
                ])
            }
            var offset = 0
            while offset < fileData.count {
                let end = min(offset + Self.chunkRawBytes, fileData.count)
                let slice = fileData.subdata(in: offset..<end)
                encodedChunks.append(slice.base64EncodedString())
                offset = end
            }
            guard encodedChunks.count <= Self.maxChunks else {
                throw NSError(domain: "SocialRepository", code: 400, userInfo: [
                    NSLocalizedDescriptionKey: "Video parça sınırı aşıldı. Lütfen daha kısa bir video seçin."
                ])
            }
        }

        if cleanUrl.isEmpty && encodedChunks.isEmpty {
            throw NSError(domain: "SocialRepository", code: 400, userInfo: [
                NSLocalizedDescriptionKey: "Lütfen bir video seçin veya geçerli bir video bağlantısı girin."
            ])
        }

        let videoRef = db.collection("videos").document()
        let profileRef = db.collection("users_public").document(uid)
        let isSingleBatch = encodedChunks.count <= Self.chunksPerBatch

        var payload: [String: Any] = [
            "userId": uid,
            "authorName": String((author.displayName.isEmpty ? "Kullanıcı" : author.displayName).prefix(60)),
            "authorUsername": String((author.username.isEmpty ? "viber" : author.username).prefix(40)),
            "authorVerified": author.isVerified || Self.isCurrentUserAdmin(auth.currentUser) || Self.isVerifiedUsername(author.username),
            "caption": String(caption.trimmingCharacters(in: .whitespacesAndNewlines).prefix(300)),
            "videoUrl": cleanUrl,
            "chunkCount": isSingleBatch ? encodedChunks.count : 0,
            "durationMs": max(0, durationMs),
            "tags": Array(tags.map { String($0.trimmingCharacters(in: .whitespacesAndNewlines).prefix(24)) }.filter { !$0.isEmpty }.prefix(5)),
            "visibility": "public",
            "likesCount": Int64(0),
            "commentsCount": Int64(0),
            "createdAt": FieldValue.serverTimestamp(),
            "updatedAt": FieldValue.serverTimestamp()
        ]
        if !author.avatarUrl.isEmpty {
            payload["authorAvatarUrl"] = String(author.avatarUrl.prefix(1000))
        }
        if let localFileName, !localFileName.isEmpty {
            payload["localFileName"] = String(localFileName.prefix(200))
        }
        if let thumbnailData, !thumbnailData.isEmpty {
            payload["thumbnailData"] = String(thumbnailData.prefix(150_000))
        }

        let firstBatchCount = min(encodedChunks.count, Self.chunksPerBatch)
        let batch = db.batch()
        batch.setData(payload, forDocument: videoRef)
        batch.updateData([
            "videosCount": FieldValue.increment(Int64(1)),
            "updatedAt": FieldValue.serverTimestamp()
        ], forDocument: profileRef)

        for idx in 0..<firstBatchCount {
            let chunkRef = videoRef.collection("media_chunks").document("chunk_\(idx)")
            batch.setData([
                "videoId": videoRef.documentID,
                "userId": uid,
                "chunkIndex": idx,
                "data": encodedChunks[idx],
                "visibility": "public",
                "createdAt": FieldValue.serverTimestamp()
            ], forDocument: chunkRef)
        }

        do {
            try await batch.commit()
        } catch {
            throw logAndWrapFirestoreError(error, operation: .create, path: videoRef.path)
        }

        if !isSingleBatch {
            var idx = firstBatchCount
            while idx < encodedChunks.count {
                let end = min(idx + Self.chunksPerBatch, encodedChunks.count)
                let nextBatch = db.batch()
                for c in idx..<end {
                    let chunkRef = videoRef.collection("media_chunks").document("chunk_\(c)")
                    nextBatch.setData([
                        "videoId": videoRef.documentID,
                        "userId": uid,
                        "chunkIndex": c,
                        "data": encodedChunks[c],
                        "visibility": "public",
                        "createdAt": FieldValue.serverTimestamp()
                    ], forDocument: chunkRef)
                }
                do {
                    try await nextBatch.commit()
                } catch {
                    throw logAndWrapFirestoreError(error, operation: .create, path: "\(videoRef.path)/media_chunks")
                }
                idx = end
            }

            try await videoRef.updateData([
                "chunkCount": encodedChunks.count,
                "updatedAt": FieldValue.serverTimestamp()
            ])
        }

        return videoRef.documentID
    }

    func downloadAndCacheCloudVideo(videoId: String, expectedChunkCount: Int) async throws -> URL {
        _ = try requireUserId()
        let cacheDir = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("SocialWiewCloudVideos", isDirectory: true)
        try FileManager.default.createDirectory(at: cacheDir, withIntermediateDirectories: true)
        let destUrl = cacheDir.appendingPathComponent("\(videoId).mp4")
        if FileManager.default.fileExists(atPath: destUrl.path),
           let attrs = try? FileManager.default.attributesOfItem(atPath: destUrl.path),
           let size = attrs[.size] as? Int64, size > 0 {
            return destUrl
        }

        let snapshot: QuerySnapshot
        do {
            snapshot = try await db.collection("videos").document(videoId).collection("media_chunks")
                .whereField("videoId", isEqualTo: videoId)
                .whereField("visibility", isEqualTo: "public")
                .getDocuments()
        } catch {
            throw logAndWrapFirestoreError(error, operation: .list, path: "videos/\(videoId)/media_chunks")
        }

        let sorted = snapshot.documents
            .compactMap { doc -> (index: Int, data: String)? in
                let d = doc.data()
                guard let idx = (d["chunkIndex"] as? NSNumber)?.intValue,
                      let b64 = d["data"] as? String else { return nil }
                return (idx, b64)
            }
            .sorted { $0.index < $1.index }

        guard !sorted.isEmpty else {
            throw NSError(domain: "SocialRepository", code: 404, userInfo: [
                NSLocalizedDescriptionKey: "Video bulut parçaları bulunamadı."
            ])
        }
        if expectedChunkCount > 0 && sorted.count < expectedChunkCount {
            throw NSError(domain: "SocialRepository", code: 206, userInfo: [
                NSLocalizedDescriptionKey: "Video parçaları eksik (\(sorted.count)/\(expectedChunkCount))."
            ])
        }

        var combined = Data()
        for chunk in sorted {
            if let decoded = Data(base64Encoded: chunk.data, options: .ignoreUnknownCharacters) {
                combined.append(decoded)
            }
        }
        try combined.write(to: destUrl, options: .atomic)
        return destUrl
    }

    func deleteVideo(videoId: String) async throws {
        let uid = try requireUserId()
        let videoRef = db.collection("videos").document(videoId)
        let profileRef = db.collection("users_public").document(uid)
        let batch = db.batch()
        batch.deleteDocument(videoRef)
        batch.updateData([
            "videosCount": FieldValue.increment(Int64(-1)),
            "updatedAt": FieldValue.serverTimestamp()
        ], forDocument: profileRef)
        do {
            try await batch.commit()
        } catch {
            throw logAndWrapFirestoreError(error, operation: .delete, path: videoRef.path)
        }
    }

    func addComment(videoId: String, author: UserPublic, text: String, gif: TenorGifItem?) async throws {
        let uid = try requireUserId()
        let cleanText = String(text.trimmingCharacters(in: .whitespacesAndNewlines).prefix(500))
        let cleanGif = gif?.fullUrl.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        guard !cleanText.isEmpty || !cleanGif.isEmpty else {
            throw NSError(domain: "SocialRepository", code: 400, userInfo: [
                NSLocalizedDescriptionKey: "Yorum metni veya GIF seçmelisiniz."
            ])
        }

        let videoRef = db.collection("videos").document(videoId)
        let commentRef = videoRef.collection("comments").document()

        var payload: [String: Any] = [
            "videoId": videoId,
            "userId": uid,
            "authorName": String((author.displayName.isEmpty ? "Kullanıcı" : author.displayName).prefix(60)),
            "authorUsername": String((author.username.isEmpty ? "viber" : author.username).prefix(40)),
            "authorVerified": author.isVerified || Self.isCurrentUserAdmin(auth.currentUser) || Self.isVerifiedUsername(author.username),
            "text": cleanText,
            "visibility": "public",
            "createdAt": FieldValue.serverTimestamp()
        ]
        if !author.avatarUrl.isEmpty {
            payload["authorAvatarUrl"] = String(author.avatarUrl.prefix(1000))
        }
        if !cleanGif.isEmpty {
            payload["gifUrl"] = String(cleanGif.prefix(1000))
        }
        if let preview = gif?.previewUrl, !preview.isEmpty {
            payload["gifPreviewUrl"] = String(preview.prefix(1000))
        }

        let batch = db.batch()
        batch.setData(payload, forDocument: commentRef)
        batch.updateData([
            "commentsCount": FieldValue.increment(Int64(1)),
            "updatedAt": FieldValue.serverTimestamp()
        ], forDocument: videoRef)

        do {
            try await batch.commit()
        } catch {
            throw logAndWrapFirestoreError(error, operation: .create, path: commentRef.path)
        }
    }

    func deleteComment(videoId: String, commentId: String) async throws {
        _ = try requireUserId()
        let videoRef = db.collection("videos").document(videoId)
        let commentRef = videoRef.collection("comments").document(commentId)
        let batch = db.batch()
        batch.deleteDocument(commentRef)
        batch.updateData([
            "commentsCount": FieldValue.increment(Int64(-1)),
            "updatedAt": FieldValue.serverTimestamp()
        ], forDocument: videoRef)

        do {
            try await batch.commit()
        } catch {
            throw logAndWrapFirestoreError(error, operation: .delete, path: commentRef.path)
        }
    }
}
