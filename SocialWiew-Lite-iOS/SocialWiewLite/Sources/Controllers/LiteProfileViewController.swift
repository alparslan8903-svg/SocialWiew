import FirebaseAuth
import UIKit

final class LiteProfileViewController: UIViewController, UICollectionViewDataSource, UICollectionViewDelegate, UICollectionViewDelegateFlowLayout {
    private let targetUserId: String?

    private let avatarView = LiteAvatarView()
    private let nameLabel = UILabel()
    private let handleLabel = UILabel()
    private let bioLabel = UILabel()
    private let statsLabel = UILabel()
    private let primaryActionButton = UIButton(type: .system)
    private let signOutButton = UIButton(type: .system)
    private let collectionView: UICollectionView

    init(userId: String?) {
        self.targetUserId = userId
        let layout = UICollectionViewFlowLayout()
        layout.minimumInteritemSpacing = 10
        layout.minimumLineSpacing = 10
        layout.sectionInset = UIEdgeInsets(top: 12, left: 12, bottom: 12, right: 12)
        self.collectionView = UICollectionView(frame: .zero, collectionViewLayout: layout)
        super.init(nibName: nil, bundle: nil)
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    deinit {
        NotificationCenter.default.removeObserver(self)
    }

    private var effectiveUserId: String {
        targetUserId ?? (LiteSocialRepository.shared.auth.currentUser?.uid ?? "")
    }

    private var isOwnProfile: Bool {
        effectiveUserId == LiteSocialRepository.shared.auth.currentUser?.uid
    }

    private var profile: LiteUserPublic? {
        let state = LiteAppState.shared
        if isOwnProfile {
            return state.currentProfile ?? state.creators.first(where: { $0.userId == effectiveUserId })
        }
        return state.creators.first(where: { $0.userId == effectiveUserId })
    }

    private var userVideos: [LiteVideoPost] {
        LiteAppState.shared.videos.filter { $0.userId == effectiveUserId }
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = LiteColors.bg
        title = isOwnProfile ? "Profil" : "Üretici"

        setupUI()
        NotificationCenter.default.addObserver(self, selector: #selector(reload), name: .liteStateDidUpdate, object: nil)
        reload()
    }

    @objc func dismissModal() {
        dismiss(animated: true)
    }

    private func setupUI() {
        let header = UIView()
        header.backgroundColor = LiteColors.surface
        header.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(header)

        avatarView.translatesAutoresizingMaskIntoConstraints = false
        nameLabel.font = .boldSystemFont(ofSize: 20)
        nameLabel.textColor = LiteColors.textPrimary
        nameLabel.textAlignment = .center

        handleLabel.font = .systemFont(ofSize: 13)
        handleLabel.textColor = LiteColors.textSecondary
        handleLabel.textAlignment = .center

        bioLabel.font = .systemFont(ofSize: 13)
        bioLabel.textColor = LiteColors.textPrimary
        bioLabel.textAlignment = .center
        bioLabel.numberOfLines = 2

        statsLabel.font = .boldSystemFont(ofSize: 13)
        statsLabel.textColor = LiteColors.electricCyan
        statsLabel.textAlignment = .center

        primaryActionButton.setTitleColor(.white, for: .normal)
        primaryActionButton.titleLabel?.font = .boldSystemFont(ofSize: 13)
        primaryActionButton.backgroundColor = LiteColors.neonCoral
        primaryActionButton.layer.cornerRadius = 10
        primaryActionButton.contentEdgeInsets = UIEdgeInsets(top: 8, left: 14, bottom: 8, right: 14)
        primaryActionButton.addTarget(self, action: #selector(primaryTapped), for: .touchUpInside)

        signOutButton.setTitle("Çıkış Yap", for: .normal)
        signOutButton.setTitleColor(LiteColors.neonCoral, for: .normal)
        signOutButton.titleLabel?.font = .boldSystemFont(ofSize: 13)
        signOutButton.backgroundColor = LiteColors.surfaceVariant
        signOutButton.layer.cornerRadius = 10
        signOutButton.contentEdgeInsets = UIEdgeInsets(top: 8, left: 14, bottom: 8, right: 14)
        signOutButton.addTarget(self, action: #selector(signOutTapped), for: .touchUpInside)

        let buttonsRow = UIStackView(arrangedSubviews: [primaryActionButton, signOutButton])
        buttonsRow.axis = .horizontal
        buttonsRow.spacing = 10

        let stack = UIStackView(arrangedSubviews: [avatarView, nameLabel, handleLabel, bioLabel, statsLabel, buttonsRow])
        stack.axis = .vertical
        stack.alignment = .center
        stack.spacing = 6
        stack.translatesAutoresizingMaskIntoConstraints = false
        header.addSubview(stack)

        collectionView.backgroundColor = LiteColors.bg
        collectionView.dataSource = self
        collectionView.delegate = self
        collectionView.register(LiteVideoTileCell.self, forCellWithReuseIdentifier: "TileCell")
        collectionView.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(collectionView)

        NSLayoutConstraint.activate([
            avatarView.widthAnchor.constraint(equalToConstant: 68),
            avatarView.heightAnchor.constraint(equalToConstant: 68),

            header.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor),
            header.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            header.trailingAnchor.constraint(equalTo: view.trailingAnchor),

            stack.topAnchor.constraint(equalTo: header.topAnchor, constant: 14),
            stack.bottomAnchor.constraint(equalTo: header.bottomAnchor, constant: -14),
            stack.leadingAnchor.constraint(equalTo: header.leadingAnchor, constant: 16),
            stack.trailingAnchor.constraint(equalTo: header.trailingAnchor, constant: -16),

            collectionView.topAnchor.constraint(equalTo: header.bottomAnchor),
            collectionView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            collectionView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            collectionView.bottomAnchor.constraint(equalTo: view.safeAreaLayoutGuide.bottomAnchor)
        ])
    }

    @objc private func reload() {
        if let profile {
            avatarView.configure(displayName: profile.displayName, avatarUrl: profile.avatarUrl, size: 68)
            nameLabel.text = profile.displayName
            handleLabel.text = "@\(profile.username)"
            bioLabel.text = profile.bio
            bioLabel.isHidden = profile.bio.isEmpty
            statsLabel.text = "\(profile.videosCount) Video · \(profile.followersCount) Takipçi · \(profile.followingCount) Takip"
        }
        signOutButton.isHidden = !isOwnProfile
        if isOwnProfile {
            primaryActionButton.setTitle("Profili Düzenle", for: .normal)
            primaryActionButton.backgroundColor = LiteColors.neonCoral
        } else {
            let following = LiteAppState.shared.followingIds.contains(effectiveUserId)
            primaryActionButton.setTitle(following ? "Takip Ediliyor" : "Takip Et", for: .normal)
            primaryActionButton.backgroundColor = following ? LiteColors.surfaceVariant : LiteColors.neonCoral
        }
        collectionView.reloadData()
    }

    @objc private func primaryTapped() {
        if isOwnProfile {
            guard let profile else { return }
            let alert = UIAlertController(title: "Profili Düzenle", message: nil, preferredStyle: .alert)
            alert.addTextField { $0.text = profile.displayName; $0.placeholder = "Görünen Ad" }
            alert.addTextField { $0.text = profile.username; $0.placeholder = "Kullanıcı Adı" }
            alert.addTextField { $0.text = profile.bio; $0.placeholder = "Biyografi" }
            alert.addAction(UIAlertAction(title: "İptal", style: .cancel))
            alert.addAction(UIAlertAction(title: "Kaydet", style: .default) { [weak self] _ in
                let name = alert.textFields?[0].text ?? ""
                let user = alert.textFields?[1].text ?? ""
                let bio = alert.textFields?[2].text ?? ""
                LiteSocialRepository.shared.updateProfile(displayName: name, username: user, bio: bio) { err in
                    if let err, let self {
                        LiteToast.show(err.localizedDescription, in: self.view)
                    }
                }
            })
            present(alert, animated: true)
        } else {
            let following = LiteAppState.shared.followingIds.contains(effectiveUserId)
            LiteSocialRepository.shared.toggleFollow(targetUserId: effectiveUserId, currentlyFollowing: following)
        }
    }

    @objc private func signOutTapped() {
        try? Auth.auth().signOut()
    }

    func collectionView(_ collectionView: UICollectionView, numberOfItemsInSection section: Int) -> Int {
        userVideos.count
    }

    func collectionView(_ collectionView: UICollectionView, layout collectionViewLayout: UICollectionViewLayout, sizeForItemAt indexPath: IndexPath) -> CGSize {
        let width = (collectionView.bounds.width - 34) / 2
        return CGSize(width: width, height: 190)
    }

    func collectionView(_ collectionView: UICollectionView, cellForItemAt indexPath: IndexPath) -> UICollectionViewCell {
        let cell = collectionView.dequeueReusableCell(withReuseIdentifier: "TileCell", for: indexPath) as! LiteVideoTileCell
        let video = userVideos[indexPath.item]
        cell.configure(video: video, showDelete: isOwnProfile) { [weak self] in
            LiteSocialRepository.shared.deleteVideo(videoId: video.id) { err in
                if let err, let self {
                    LiteToast.show(err.localizedDescription, in: self.view)
                }
            }
        }
        return cell
    }

    func collectionView(_ collectionView: UICollectionView, didSelectItemAt indexPath: IndexPath) {
        present(LiteSingleVideoPlayerController(video: userVideos[indexPath.item]), animated: true)
    }
}
