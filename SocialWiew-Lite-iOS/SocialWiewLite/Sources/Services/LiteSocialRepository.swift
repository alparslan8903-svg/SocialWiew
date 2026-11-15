import Foundation
import FirebaseAuth
import FirebaseFirestore

final class LiteSocialRepository {
    static let shared = LiteSocialRepository()

    static let adminEmail = "alparslan8903@gmail.com"
    private static let verifiedUsernames: Set<String> = ["alparslan8903", "eymendogan615"]
    private static let maxCloudVideoBytes = 15_500_000
    private static let chunkRawBytes = 450_000
    private static let maxChunks = 36
    private static let chunksPerBatch = 6
    private static let maxDiskCacheBytes: Int64 = 60 * 1024 * 1024

    let db: Firestore
    let auth: Auth

    private init() {
        self.db = Firestore.firestore(database: Self.loadConfiguredDatabaseId())
        self.auth = Auth.auth()
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

    func ensureUserProfile(completion: @escaping (Result<LiteUserPublic, Error>) -> Void) {
        guard let current = auth.currentUser else {
            completion(.failure(NSError(domain: "LiteSocialRepository", code: 401, userInfo: [
                NSLocalizedDescriptionKey: "Kullanıcı oturumu bulunamadı."
            ])))
            return
        }

        let uid = current.uid
        let publicRef = db.collection("users_public").document(uid)
        let privateRef = db.collection("users_private").document(uid)

        publicRef.getDocument { [weak self] snapshot, error in
            guard let self else { return }
            if let error {
                completion(.failure(error))
                return
            }

            let isAdmin = Self.isCurrentUserAdmin(current)
            if let snapshot, snapshot.exists, var profile = LiteUserPublic(document: snapshot) {
                let shouldVerify = isAdmin || Self.isVerifiedUsername(profile.username)
                if shouldVerify && !profile.isVerified {
                    publicRef.updateData([
                        "isVerified": true,
                        "updatedAt": FieldValue.serverTimestamp()
                    ])
                    profile.isVerified = true
                }
                completion(.success(profile))
                return
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

            let batch = self.db.batch()
            batch.setData(publicPayload, forDocument: publicRef)
            batch.setData(privatePayload, forDocument: privateRef)
            batch.commit { err in
                if let err {
                    completion(.failure(err))
                    return
                }
                completion(.success(LiteUserPublic(
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
                    isPublic: true
                )))
            }
        }
    }

    func observePublicVideos(limit: Int = 20, onUpdate: @escaping ([LiteVideoPost]) -> Void) -> ListenerRegistration {
        db.collection("videos")
            .whereField("visibility", isEqualTo: "public")
            .limit(to: limit)
            .addSnapshotListener { snapshot, _ in
                let list = (snapshot?.documents ?? [])
                    .compactMap(LiteVideoPost.init(document:))
                    .sorted { ($0.createdAt ?? .distantPast) > ($1.createdAt ?? .distantPast) }
                onUpdate(list)
            }
    }

    func observeCreators(limit: Int = 30, onUpdate: @escaping ([LiteUserPublic]) -> Void) -> ListenerRegistration {
        db.collection("users_public")
            .whereField("isPublic", isEqualTo: true)
            .limit(to: limit)
            .addSnapshotListener { snapshot, _ in
                let list = (snapshot?.documents ?? [])
                    .compactMap(LiteUserPublic.init(document:))
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

    func observeComments(videoId: String, onUpdate: @escaping ([LiteVideoComment]) -> Void) -> ListenerRegistration {
        db.collection("videos").document(videoId).collection("comments")
            .whereField("videoId", isEqualTo: videoId)
            .whereField("visibility", isEqualTo: "public")
            .limit(to: 50)
            .addSnapshotListener { snapshot, _ in
                let list = (snapshot?.documents ?? [])
                    .compactMap(LiteVideoComment.init(document:))
                    .sorted { ($0.createdAt ?? .distantPast) > ($1.createdAt ?? .distantPast) }
                onUpdate(list)
            }
    }

    func toggleLike(videoId: String, currentlyLiked: Bool, completion: ((Error?) -> Void)? = nil) {
        guard let uid = auth.currentUser?.uid else {
            completion?(NSError(domain: "LiteSocialRepository", code: 401, userInfo: [NSLocalizedDescriptionKey: "Giriş yapmalısınız."]))
            return
        }
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
        batch.commit { err in completion?(err) }
    }

    func toggleFollow(targetUserId: String, currentlyFollowing: Bool, completion: ((Error?) -> Void)? = nil) {
        guard let uid = auth.currentUser?.uid, uid != targetUserId else {
            completion?(nil)
            return
        }
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
        batch.commit { err in completion?(err) }
    }

    func addComment(videoId: String, author: LiteUserPublic, text: String, gif: LiteTenorGifItem?, completion: @escaping (Error?) -> Void) {
        guard let uid = auth.currentUser?.uid else {
            completion(NSError(domain: "LiteSocialRepository", code: 401, userInfo: [NSLocalizedDescriptionKey: "Giriş yapmalısınız."]))
            return
        }
        let cleanText = String(text.trimmingCharacters(in: .whitespacesAndNewlines).prefix(500))
        let cleanGif = gif?.fullUrl.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        guard !cleanText.isEmpty || !cleanGif.isEmpty else {
            completion(NSError(domain: "LiteSocialRepository", code: 400, userInfo: [NSLocalizedDescriptionKey: "Yorum veya GIF seçin."]))
            return
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
        batch.commit(completion: completion)
    }

    func deleteComment(videoId: String, commentId: String, completion: ((Error?) -> Void)? = nil) {
        let videoRef = db.collection("videos").document(videoId)
        let commentRef = videoRef.collection("comments").document(commentId)
        let batch = db.batch()
        batch.deleteDocument(commentRef)
        batch.updateData([
            "commentsCount": FieldValue.increment(Int64(-1)),
            "updatedAt": FieldValue.serverTimestamp()
        ], forDocument: videoRef)
        batch.commit { err in completion?(err) }
    }

    func updateProfile(displayName: String, username: String, bio: String, completion: @escaping (Error?) -> Void) {
        guard let uid = auth.currentUser?.uid else {
            completion(NSError(domain: "LiteSocialRepository", code: 401, userInfo: [NSLocalizedDescriptionKey: "Oturum bulunamadı."]))
            return
        }
        let cleanDisplay = String(displayName.trimmingCharacters(in: .whitespacesAndNewlines).prefix(50))
        let cleanUser = String(
            username.trimmingCharacters(in: .whitespacesAndNewlines)
                .lowercased()
                .replacingOccurrences(of: "[^a-z0-9_.]", with: "", options: .regularExpression)
                .prefix(30)
        )
        guard !cleanDisplay.isEmpty, cleanUser.count >= 3 else {
            completion(NSError(domain: "LiteSocialRepository", code: 400, userInfo: [NSLocalizedDescriptionKey: "Geçerli isim ve kullanıcı adı (min 3 karakter) girin."]))
            return
        }
        db.collection("users_public").document(uid).updateData([
            "displayName": cleanDisplay,
            "username": cleanUser,
            "bio": String(bio.trimmingCharacters(in: .whitespacesAndNewlines).prefix(200)),
            "updatedAt": FieldValue.serverTimestamp()
        ], completion: completion)
    }

    func deleteVideo(videoId: String, completion: @escaping (Error?) -> Void) {
        guard let uid = auth.currentUser?.uid else {
            completion(NSError(domain: "LiteSocialRepository", code: 401, userInfo: [NSLocalizedDescriptionKey: "Oturum bulunamadı."]))
            return
        }
        let videoRef = db.collection("videos").document(videoId)
        let profileRef = db.collection("users_public").document(uid)
        let batch = db.batch()
        batch.deleteDocument(videoRef)
        batch.updateData([
            "videosCount": FieldValue.increment(Int64(-1)),
            "updatedAt": FieldValue.serverTimestamp()
        ], forDocument: profileRef)
        batch.commit(completion: completion)
    }

    func publishVideo(
        author: LiteUserPublic,
        caption: String,
        videoUrl: String,
        localFileData: Data?,
        localFileName: String?,
        thumbnailData: String?,
        durationMs: Int64,
        tags: [String],
        completion: @escaping (Result<String, Error>) -> Void
    ) {
        guard let uid = auth.currentUser?.uid else {
            completion(.failure(NSError(domain: "LiteSocialRepository", code: 401, userInfo: [NSLocalizedDescriptionKey: "Giriş yapmalısınız."])))
            return
        }
        let cleanUrl = String(videoUrl.trimmingCharacters(in: .whitespacesAndNewlines).prefix(2000))

        DispatchQueue.global(qos: .userInitiated).async {
            var encodedChunks: [String] = []
            if let fileData = localFileData, !fileData.isEmpty {
                guard fileData.count <= Self.maxCloudVideoBytes else {
                    DispatchQueue.main.async {
                        completion(.failure(NSError(domain: "LiteSocialRepository", code: 400, userInfo: [
                            NSLocalizedDescriptionKey: "Video boyutu en fazla 15 MB olabilir."
                        ])))
                    }
                    return
                }
                var offset = 0
                while offset < fileData.count {
                    let end = min(offset + Self.chunkRawBytes, fileData.count)
                    let slice = fileData.subdata(in: offset..<end)
                    encodedChunks.append(slice.base64EncodedString())
                    offset = end
                }
                guard encodedChunks.count <= Self.maxChunks else {
                    DispatchQueue.main.async {
                        completion(.failure(NSError(domain: "LiteSocialRepository", code: 400, userInfo: [
                            NSLocalizedDescriptionKey: "Video parça sınırı aşıldı."
                        ])))
                    }
                    return
                }
            }

            if cleanUrl.isEmpty && encodedChunks.isEmpty {
                DispatchQueue.main.async {
                    completion(.failure(NSError(domain: "LiteSocialRepository", code: 400, userInfo: [
                        NSLocalizedDescriptionKey: "Video seçin veya HTTPS bağlantı girin."
                    ])))
                }
                return
            }

            let videoRef = self.db.collection("videos").document()
            let profileRef = self.db.collection("users_public").document(uid)
            let isSingleBatch = encodedChunks.count <= Self.chunksPerBatch

            var payload: [String: Any] = [
                "userId": uid,
                "authorName": String((author.displayName.isEmpty ? "Kullanıcı" : author.displayName).prefix(60)),
                "authorUsername": String((author.username.isEmpty ? "viber" : author.username).prefix(40)),
                "authorVerified": author.isVerified || Self.isCurrentUserAdmin(self.auth.currentUser) || Self.isVerifiedUsername(author.username),
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
            let batch = self.db.batch()
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

            batch.commit { error in
                if let error {
                    DispatchQueue.main.async { completion(.failure(error)) }
                    return
                }
                if isSingleBatch {
                    DispatchQueue.main.async { completion(.success(videoRef.documentID)) }
                    return
                }
                self.uploadRemainingChunks(
                    videoRef: videoRef,
                    uid: uid,
                    chunks: encodedChunks,
                    startIndex: firstBatchCount,
                    completion: completion
                )
            }
        }
    }

    private func uploadRemainingChunks(
        videoRef: DocumentReference,
        uid: String,
        chunks: [String],
        startIndex: Int,
        completion: @escaping (Result<String, Error>) -> Void
    ) {
        if startIndex >= chunks.count {
            videoRef.updateData([
                "chunkCount": chunks.count,
                "updatedAt": FieldValue.serverTimestamp()
            ]) { err in
                DispatchQueue.main.async {
                    if let err {
                        completion(.failure(err))
                    } else {
                        completion(.success(videoRef.documentID))
                    }
                }
            }
            return
        }

        let end = min(startIndex + Self.chunksPerBatch, chunks.count)
        let batch = db.batch()
        for c in startIndex..<end {
            let chunkRef = videoRef.collection("media_chunks").document("chunk_\(c)")
            batch.setData([
                "videoId": videoRef.documentID,
                "userId": uid,
                "chunkIndex": c,
                "data": chunks[c],
                "visibility": "public",
                "createdAt": FieldValue.serverTimestamp()
            ], forDocument: chunkRef)
        }
        batch.commit { [weak self] err in
            if let err {
                DispatchQueue.main.async { completion(.failure(err)) }
                return
            }
            self?.uploadRemainingChunks(videoRef: videoRef, uid: uid, chunks: chunks, startIndex: end, completion: completion)
        }
    }

    func downloadAndCacheCloudVideo(videoId: String, expectedChunkCount: Int, completion: @escaping (Result<URL, Error>) -> Void) {
        let cacheDir = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("SocialWiewLiteVideos", isDirectory: true)
        try? FileManager.default.createDirectory(at: cacheDir, withIntermediateDirectories: true)

        let destUrl = cacheDir.appendingPathComponent("\(videoId).mp4")
        if FileManager.default.fileExists(atPath: destUrl.path),
           let attrs = try? FileManager.default.attributesOfItem(atPath: destUrl.path),
           let size = attrs[.size] as? Int64, size > 0 {
            completion(.success(destUrl))
            return
        }

        db.collection("videos").document(videoId).collection("media_chunks")
            .whereField("videoId", isEqualTo: videoId)
            .whereField("visibility", isEqualTo: "public")
            .getDocuments { snapshot, error in
                if let error {
                    completion(.failure(error))
                    return
                }
                DispatchQueue.global(qos: .userInitiated).async {
                    let sorted = (snapshot?.documents ?? [])
                        .compactMap { doc -> (index: Int, data: String)? in
                            let d = doc.data()
                            guard let idx = (d["chunkIndex"] as? NSNumber)?.intValue,
                                  let b64 = d["data"] as? String else { return nil }
                            return (idx, b64)
                        }
                        .sorted { $0.index < $1.index }

                    guard !sorted.isEmpty else {
                        DispatchQueue.main.async {
                            completion(.failure(NSError(domain: "LiteSocialRepository", code: 404, userInfo: [
                                NSLocalizedDescriptionKey: "Video parçaları bulunamadı."
                            ])))
                        }
                        return
                    }
                    if expectedChunkCount > 0 && sorted.count < expectedChunkCount {
                        DispatchQueue.main.async {
                            completion(.failure(NSError(domain: "LiteSocialRepository", code: 206, userInfo: [
                                NSLocalizedDescriptionKey: "Video parçaları eksik."
                            ])))
                        }
                        return
                    }

                    var combined = Data()
                    for chunk in sorted {
                        if let decoded = Data(base64Encoded: chunk.data, options: .ignoreUnknownCharacters) {
                            combined.append(decoded)
                        }
                    }

                    do {
                        Self.trimDiskCacheIfNeeded(in: cacheDir)
                        try combined.write(to: destUrl, options: .atomic)
                        DispatchQueue.main.async { completion(.success(destUrl)) }
                    } catch {
                        DispatchQueue.main.async { completion(.failure(error)) }
                    }
                }
            }
    }

    private static func trimDiskCacheIfNeeded(in dir: URL) {
        let fm = FileManager.default
        guard let urls = try? fm.contentsOfDirectory(at: dir, includingPropertiesForKeys: [.fileSizeKey, .contentModificationDateKey]) else {
            return
        }
        var items: [(url: URL, size: Int64, date: Date)] = urls.compactMap { url in
            let vals = try? url.resourceValues(forKeys: [.fileSizeKey, .contentModificationDateKey])
            let size = Int64(vals?.fileSize ?? 0)
            let date = vals?.contentModificationDate ?? Date.distantPast
            return (url, size, date)
        }
        var total = items.reduce(Int64(0)) { $0 + $1.size }
        items.sort { $0.date < $1.date }
        for item in items where total > maxDiskCacheBytes {
            try? fm.removeItem(at: item.url)
            total -= item.size
        }
    }
}
