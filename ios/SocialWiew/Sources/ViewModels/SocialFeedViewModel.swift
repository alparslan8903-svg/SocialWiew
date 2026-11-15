import Foundation
import FirebaseAuth
import FirebaseFirestore
import SwiftUI

@MainActor
final class SocialFeedViewModel: ObservableObject {
    @Published var currentUser: User?
    @Published var currentProfile: UserPublic?
    @Published var videos: [VideoPost] = []
    @Published var creators: [UserPublic] = []
    @Published var followingIds: Set<String> = []
    @Published var likedVideoIds: Set<String> = []
    @Published var comments: [VideoComment] = []
    @Published var selectedVideoForComments: VideoPost?

    @Published var tenorGifs: [TenorGifItem] = []
    @Published var tenorLoading = false
    @Published var tenorError: String?

    @Published var configuredGithubRepo = ""
    @Published var githubUpdateInfo: AppUpdateInfo?
    @Published var githubUpdateLoading = false
    @Published var githubUpdateError: String?

    @Published var isPublishing = false
    @Published var toastMessage: String?
    @Published var themeMode: AppThemeMode

    private let repository: SocialRepository
    private let tenorService: TenorService
    private let githubService: GitHubUpdateService

    private var authHandle: AuthStateDidChangeListenerHandle?
    private var listeners: [ListenerRegistration] = []
    private var commentsListener: ListenerRegistration?
    private static let themeModeKey = "socialwiew_theme_mode"

    init(
        repository: SocialRepository = SocialRepository(),
        tenorService: TenorService = TenorService(),
        githubService: GitHubUpdateService = GitHubUpdateService()
    ) {
        self.repository = repository
        self.tenorService = tenorService
        self.githubService = githubService
        let stored = UserDefaults.standard.string(forKey: Self.themeModeKey) ?? AppThemeMode.dark.rawValue
        self.themeMode = AppThemeMode(rawValue: stored) ?? .dark
        self.currentUser = repository.auth.currentUser
        observeAuth()
    }

    deinit {
        if let authHandle {
            Auth.auth().removeStateDidChangeListener(authHandle)
        }
        listeners.forEach { $0.remove() }
        commentsListener?.remove()
    }

    var verifiedUserIds: Set<String> {
        Set(creators.filter { $0.isVerified || SocialRepository.isVerifiedUsername($0.username) }.map(\.userId))
    }

    var isAdmin: Bool {
        SocialRepository.isCurrentUserAdmin(currentUser)
    }

    func setThemeMode(_ mode: AppThemeMode) {
        themeMode = mode
        UserDefaults.standard.set(mode.rawValue, forKey: Self.themeModeKey)
    }

    private func observeAuth() {
        authHandle = repository.auth.addStateDidChangeListener { [weak self] _, user in
            Task { @MainActor [weak self] in
                guard let self else { return }
                self.currentUser = user
                self.stopDataListeners()
                guard let user else {
                    self.currentProfile = nil
                    self.videos = []
                    self.creators = []
                    self.followingIds = []
                    self.likedVideoIds = []
                    return
                }
                await self.repository.testConnection()
                do {
                    self.currentProfile = try await self.repository.ensureUserProfile()
                } catch {
                    self.showToast(error.localizedDescription)
                }
                self.startDataListeners(userId: user.uid)
            }
        }
    }

    private func stopDataListeners() {
        listeners.forEach { $0.remove() }
        listeners.removeAll()
        commentsListener?.remove()
        commentsListener = nil
    }

    private func startDataListeners(userId: String) {
        listeners.append(
            repository.observePublicVideos(
                onUpdate: { [weak self] list in
                    self?.videos = list
                },
                onError: { [weak self] err in
                    self?.showToast(err.localizedDescription)
                }
            )
        )
        listeners.append(
            repository.observeCreators(
                onUpdate: { [weak self] list in
                    self?.creators = list
                    if let updated = list.first(where: { $0.userId == userId }) {
                        self?.currentProfile = updated
                    }
                },
                onError: { [weak self] err in
                    self?.showToast(err.localizedDescription)
                }
            )
        )
        listeners.append(
            repository.observeFollowingIds(userId: userId) { [weak self] ids in
                self?.followingIds = ids
            }
        )
        listeners.append(
            repository.observeLikedVideoIds(userId: userId) { [weak self] ids in
                self?.likedVideoIds = ids
            }
        )
        listeners.append(
            repository.observeSharedUpdateRepo { [weak self] repo in
                self?.configuredGithubRepo = repo
            }
        )
    }

    func toggleLike(video: VideoPost) {
        let liked = likedVideoIds.contains(video.id)
        Task {
            do {
                try await repository.toggleLike(videoId: video.id, currentlyLiked: liked)
            } catch {
                showToast(error.localizedDescription)
            }
        }
    }

    func toggleFollow(targetUserId: String) {
        guard targetUserId != currentUser?.uid else { return }
        let following = followingIds.contains(targetUserId)
        Task {
            do {
                try await repository.toggleFollow(targetUserId: targetUserId, currentlyFollowing: following)
            } catch {
                showToast(error.localizedDescription)
            }
        }
    }

    func openComments(for video: VideoPost) {
        selectedVideoForComments = video
        commentsListener?.remove()
        comments = []
        commentsListener = repository.observeComments(videoId: video.id) { [weak self] items in
            self?.comments = items
        }
        if tenorGifs.isEmpty {
            loadTenorGifs(query: "alkış")
        }
    }

    func closeComments() {
        selectedVideoForComments = nil
        commentsListener?.remove()
        commentsListener = nil
        comments = []
    }

    func loadTenorGifs(query: String) {
        tenorLoading = true
        tenorError = nil
        Task {
            do {
                let gifs = try await tenorService.loadGifs(query: query)
                tenorGifs = gifs
            } catch {
                tenorError = error.localizedDescription
            }
            tenorLoading = false
        }
    }

    func addComment(videoId: String, text: String, gif: TenorGifItem?) {
        guard let author = currentProfile else {
            showToast("Profiliniz henüz hazır değil.")
            return
        }
        Task {
            do {
                try await repository.addComment(videoId: videoId, author: author, text: text, gif: gif)
            } catch {
                showToast(error.localizedDescription)
            }
        }
    }

    func deleteComment(videoId: String, commentId: String) {
        Task {
            do {
                try await repository.deleteComment(videoId: videoId, commentId: commentId)
            } catch {
                showToast(error.localizedDescription)
            }
        }
    }

    func publishVideo(
        caption: String,
        videoUrl: String,
        localFileData: Data?,
        localFileName: String?,
        thumbnailData: String?,
        durationMs: Int64,
        tags: [String],
        onComplete: @escaping () -> Void
    ) {
        guard let author = currentProfile else {
            showToast("Profiliniz yüklenemedi.")
            return
        }
        isPublishing = true
        Task {
            do {
                _ = try await repository.publishVideo(
                    author: author,
                    caption: caption,
                    videoUrl: videoUrl,
                    localFileData: localFileData,
                    localFileName: localFileName,
                    thumbnailData: thumbnailData,
                    durationMs: durationMs,
                    tags: tags
                )
                showToast("Video başarıyla paylaşıldı!")
                onComplete()
            } catch {
                showToast(error.localizedDescription)
            }
            isPublishing = false
        }
    }

    func resolveCloudVideoURL(videoId: String, chunkCount: Int) async throws -> URL {
        try await repository.downloadAndCacheCloudVideo(videoId: videoId, expectedChunkCount: chunkCount)
    }

    func deleteVideo(videoId: String) {
        Task {
            do {
                try await repository.deleteVideo(videoId: videoId)
                showToast("Video silindi.")
            } catch {
                showToast(error.localizedDescription)
            }
        }
    }

    func updateProfile(displayName: String, username: String, bio: String) async throws {
        try await repository.updateProfile(displayName: displayName, username: username, bio: bio)
        showToast("Profil güncellendi.")
    }

    func setUserVerified(targetUserId: String, verified: Bool) {
        Task {
            do {
                try await repository.setUserVerified(targetUserId: targetUserId, verified: verified)
                showToast(verified ? "Mavi tik verildi." : "Mavi tik kaldırıldı.")
            } catch {
                showToast(error.localizedDescription)
            }
        }
    }

    func configureGithubRepo(_ repo: String) {
        Task {
            do {
                try await repository.saveSharedUpdateRepo(repo)
                showToast("Güncelleme deposu kaydedildi.")
                checkForGitHubUpdates()
            } catch {
                showToast(error.localizedDescription)
            }
        }
    }

    func checkForGitHubUpdates() {
        let repo = configuredGithubRepo.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !repo.isEmpty else {
            showToast("Önce bir GitHub deposu yapılandırın.")
            return
        }
        let currentVersion = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "1.0"
        githubUpdateLoading = true
        githubUpdateError = nil
        Task {
            do {
                githubUpdateInfo = try await githubService.checkForUpdate(rawRepoInput: repo, currentVersion: currentVersion)
            } catch {
                githubUpdateError = error.localizedDescription
            }
            githubUpdateLoading = false
        }
    }

    func signOut() {
        do {
            try repository.auth.signOut()
        } catch {
            showToast(error.localizedDescription)
        }
    }

    func showToast(_ message: String) {
        toastMessage = message
        Task {
            try? await Task.sleep(for: .seconds(3))
            if toastMessage == message {
                toastMessage = nil
            }
        }
    }
}
