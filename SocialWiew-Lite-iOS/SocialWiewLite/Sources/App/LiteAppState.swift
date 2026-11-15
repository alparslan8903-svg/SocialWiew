import Foundation
import FirebaseAuth
import FirebaseFirestore

extension Notification.Name {
    static let liteStateDidUpdate = Notification.Name("SocialWiewLiteStateDidUpdate")
}

final class LiteAppState {
    static let shared = LiteAppState()

    private(set) var currentProfile: LiteUserPublic?
    private(set) var videos: [LiteVideoPost] = []
    private(set) var creators: [LiteUserPublic] = []
    private(set) var followingIds: Set<String> = []
    private(set) var likedVideoIds: Set<String> = []

    private var listeners: [ListenerRegistration] = []

    private init() {}

    func startSession() {
        stopSession()
        let repo = LiteSocialRepository.shared
        guard let uid = repo.auth.currentUser?.uid else { return }

        repo.ensureUserProfile { [weak self] result in
            if case .success(let profile) = result {
                self?.currentProfile = profile
                NotificationCenter.default.post(name: .liteStateDidUpdate, object: nil)
            }
        }

        listeners.append(repo.observePublicVideos(limit: 20) { [weak self] list in
            self?.videos = list
            NotificationCenter.default.post(name: .liteStateDidUpdate, object: nil)
        })

        listeners.append(repo.observeCreators(limit: 30) { [weak self] list in
            self?.creators = list
            if let uid = repo.auth.currentUser?.uid,
               let mine = list.first(where: { $0.userId == uid }) {
                self?.currentProfile = mine
            }
            NotificationCenter.default.post(name: .liteStateDidUpdate, object: nil)
        })

        listeners.append(repo.observeFollowingIds(userId: uid) { [weak self] ids in
            self?.followingIds = ids
            NotificationCenter.default.post(name: .liteStateDidUpdate, object: nil)
        })

        listeners.append(repo.observeLikedVideoIds(userId: uid) { [weak self] ids in
            self?.likedVideoIds = ids
            NotificationCenter.default.post(name: .liteStateDidUpdate, object: nil)
        })
    }

    func stopSession() {
        listeners.forEach { $0.remove() }
        listeners.removeAll()
        currentProfile = nil
        videos = []
        creators = []
        followingIds = []
        likedVideoIds = []
    }
}
