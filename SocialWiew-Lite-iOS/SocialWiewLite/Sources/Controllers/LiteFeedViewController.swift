import AVFoundation
import UIKit

final class LiteFeedViewController: UIViewController, UICollectionViewDataSource, UICollectionViewDelegate, UICollectionViewDelegateFlowLayout {
    private enum FilterTab: Int {
        case forYou = 0
        case following = 1
    }

    private let collectionView: UICollectionView
    private let segmentedControl = UISegmentedControl(items: ["Senin İçin", "Takip"])
    private let emptyLabel = UILabel()

    private var activeFilter: FilterTab = .forYou
    private var isMuted = false
    private var activeIndex = 0
    private var displayedVideos: [LiteVideoPost] {
        let state = LiteAppState.shared
        switch activeFilter {
        case .forYou:
            return state.videos
        case .following:
            return state.videos.filter { state.followingIds.contains($0.userId) }
        }
    }

    init() {
        let layout = UICollectionViewFlowLayout()
        layout.scrollDirection = .vertical
        layout.minimumLineSpacing = 0
        layout.minimumInteritemSpacing = 0
        self.collectionView = UICollectionView(frame: .zero, collectionViewLayout: layout)
        super.init(nibName: nil, bundle: nil)
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    deinit {
        NotificationCenter.default.removeObserver(self)
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = LiteColors.bg

        collectionView.backgroundColor = LiteColors.bg
        collectionView.isPagingEnabled = true
        collectionView.showsVerticalScrollIndicator = false
        collectionView.contentInsetAdjustmentBehavior = .never
        collectionView.dataSource = self
        collectionView.delegate = self
        collectionView.register(LiteFeedCell.self, forCellWithReuseIdentifier: "FeedCell")
        collectionView.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(collectionView)

        segmentedControl.selectedSegmentIndex = 0
        segmentedControl.tintColor = LiteColors.neonCoral
        segmentedControl.backgroundColor = LiteColors.surface.withAlphaComponent(0.9)
        segmentedControl.setTitleTextAttributes([.foregroundColor: LiteColors.textPrimary], for: .normal)
        segmentedControl.setTitleTextAttributes([.foregroundColor: UIColor.white], for: .selected)
        segmentedControl.addTarget(self, action: #selector(filterChanged), for: .valueChanged)
        segmentedControl.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(segmentedControl)

        emptyLabel.text = "Henüz video yok."
        emptyLabel.textColor = LiteColors.textSecondary
        emptyLabel.font = .systemFont(ofSize: 15, weight: .medium)
        emptyLabel.textAlignment = .center
        emptyLabel.numberOfLines = 0
        emptyLabel.isHidden = true
        emptyLabel.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(emptyLabel)

        NSLayoutConstraint.activate([
            collectionView.topAnchor.constraint(equalTo: view.topAnchor),
            collectionView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            collectionView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            collectionView.bottomAnchor.constraint(equalTo: view.safeAreaLayoutGuide.bottomAnchor),

            segmentedControl.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor, constant: 10),
            segmentedControl.centerXAnchor.constraint(equalTo: view.centerXAnchor),
            segmentedControl.widthAnchor.constraint(equalToConstant: 210),

            emptyLabel.centerXAnchor.constraint(equalTo: view.centerXAnchor),
            emptyLabel.centerYAnchor.constraint(equalTo: view.centerYAnchor),
            emptyLabel.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 32),
            emptyLabel.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -32)
        ])

        NotificationCenter.default.addObserver(self, selector: #selector(stateUpdated), name: .liteStateDidUpdate, object: nil)
        reloadData()
    }

    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        playVisibleCell()
    }

    override func viewWillDisappear(_ animated: Bool) {
        super.viewWillDisappear(animated)
        pauseAllCells()
    }

    @objc private func stateUpdated() {
        reloadData()
    }

    @objc private func filterChanged() {
        activeFilter = FilterTab(rawValue: segmentedControl.selectedSegmentIndex) ?? .forYou
        activeIndex = 0
        if !displayedVideos.isEmpty {
            collectionView.setContentOffset(.zero, animated: false)
        }
        reloadData()
    }

    private func reloadData() {
        let items = displayedVideos
        emptyLabel.isHidden = !items.isEmpty
        emptyLabel.text = activeFilter == .following
            ? "Takip ettiğin üreticilerden henüz video yok."
            : "Akışta henüz video yok."
        collectionView.reloadData()
        DispatchQueue.main.async { [weak self] in
            self?.playVisibleCell()
        }
    }

    private func pauseAllCells() {
        for case let cell as LiteFeedCell in collectionView.visibleCells {
            cell.stopPlayback()
        }
    }

    private func playVisibleCell() {
        let items = displayedVideos
        guard !items.isEmpty else { return }
        let pageHeight = max(1, collectionView.bounds.height)
        let current = max(0, min(items.count - 1, Int(round(collectionView.contentOffset.y / pageHeight))))
        activeIndex = current
        for case let cell as LiteFeedCell in collectionView.visibleCells {
            if let ip = collectionView.indexPath(for: cell), ip.item == current {
                cell.startPlayback(muted: isMuted)
            } else {
                cell.stopPlayback()
            }
        }
    }

    func scrollViewDidEndDecelerating(_ scrollView: UIScrollView) {
        playVisibleCell()
    }

    func scrollViewDidEndDragging(_ scrollView: UIScrollView, willDecelerate decelerate: Bool) {
        if !decelerate {
            playVisibleCell()
        }
    }

    func collectionView(_ collectionView: UICollectionView, numberOfItemsInSection section: Int) -> Int {
        displayedVideos.count
    }

    func collectionView(_ collectionView: UICollectionView, layout collectionViewLayout: UICollectionViewLayout, sizeForItemAt indexPath: IndexPath) -> CGSize {
        collectionView.bounds.size
    }

    func collectionView(_ collectionView: UICollectionView, didEndDisplaying cell: UICollectionViewCell, forItemAt indexPath: IndexPath) {
        (cell as? LiteFeedCell)?.stopPlayback()
    }

    func collectionView(_ collectionView: UICollectionView, cellForItemAt indexPath: IndexPath) -> UICollectionViewCell {
        let cell = collectionView.dequeueReusableCell(withReuseIdentifier: "FeedCell", for: indexPath) as! LiteFeedCell
        let video = displayedVideos[indexPath.item]
        let state = LiteAppState.shared
        let myUid = LiteSocialRepository.shared.auth.currentUser?.uid ?? ""
        let isOwn = video.userId == myUid
        let isFollowing = state.followingIds.contains(video.userId)
        let isLiked = state.likedVideoIds.contains(video.id)

        cell.configure(
            video: video,
            isOwnVideo: isOwn,
            isFollowing: isFollowing,
            isLiked: isLiked,
            isMuted: isMuted,
            onLike: {
                LiteSocialRepository.shared.toggleLike(videoId: video.id, currentlyLiked: isLiked)
            },
            onFollow: {
                LiteSocialRepository.shared.toggleFollow(targetUserId: video.userId, currentlyFollowing: isFollowing)
            },
            onComment: { [weak self] in
                let comments = UINavigationController(rootViewController: LiteCommentsViewController(video: video))
                comments.navigationBar.barTintColor = LiteColors.surface
                comments.navigationBar.tintColor = LiteColors.neonCoral
                comments.navigationBar.titleTextAttributes = [.foregroundColor: LiteColors.textPrimary]
                self?.present(comments, animated: true)
            },
            onShare: { [weak self] in
                let shareText = video.videoUrl.isEmpty ? "SocialWiew: \(video.caption)" : video.videoUrl
                let act = UIActivityViewController(activityItems: [shareText], applicationActivities: nil)
                self?.present(act, animated: true)
            },
            onMuteToggle: { [weak self] in
                guard let self else { return }
                self.isMuted.toggle()
                self.playVisibleCell()
                self.collectionView.reloadData()
            },
            onOpenProfile: { [weak self] in
                let profileVC = LiteProfileViewController(userId: video.userId)
                let nav = UINavigationController(rootViewController: profileVC)
                nav.navigationBar.barTintColor = LiteColors.surface
                nav.navigationBar.tintColor = LiteColors.neonCoral
                nav.navigationBar.titleTextAttributes = [.foregroundColor: LiteColors.textPrimary]
                profileVC.navigationItem.leftBarButtonItem = UIBarButtonItem(title: "Kapat", style: .plain, target: profileVC, action: #selector(LiteProfileViewController.dismissModal))
                self?.present(nav, animated: true)
            }
        )
        return cell
    }
}

final class LiteFeedCell: UICollectionViewCell {
    private let playerContainer = UIView()
    private let thumbView = UIImageView()
    private let statusLabel = UILabel()
    private let avatarView = LiteAvatarView()
    private let authorButton = UIButton(type: .system)
    private let verifiedView = UIImageView()
    private let followButton = UIButton(type: .system)
    private let captionLabel = UILabel()
    private let tagsLabel = UILabel()

    private let likeButton = UIButton(type: .system)
    private let likeCountLabel = UILabel()
    private let commentButton = UIButton(type: .system)
    private let commentCountLabel = UILabel()
    private let shareButton = UIButton(type: .system)
    private let muteButton = UIButton(type: .system)

    private var player: AVPlayer?
    private var playerLayer: AVPlayerLayer?
    private var loopObserver: NSObjectProtocol?
    private var currentVideo: LiteVideoPost?

    private var onLike: (() -> Void)?
    private var onFollow: (() -> Void)?
    private var onComment: (() -> Void)?
    private var onShare: (() -> Void)?
    private var onMuteToggle: (() -> Void)?
    private var onOpenProfile: (() -> Void)?

    override init(frame: CGRect) {
        super.init(frame: frame)
        backgroundColor = UIColor(hex: 0x06070B)
        setupUI()
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override func prepareForReuse() {
        super.prepareForReuse()
        stopPlayback()
        thumbView.image = nil
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        playerLayer?.frame = playerContainer.bounds
    }

    private func setupUI() {
        playerContainer.frame = bounds
        playerContainer.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        contentView.addSubview(playerContainer)

        thumbView.contentMode = .scaleAspectFill
        thumbView.clipsToBounds = true
        thumbView.frame = bounds
        thumbView.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        playerContainer.addSubview(thumbView)

        statusLabel.font = .systemFont(ofSize: 12, weight: .medium)
        statusLabel.textColor = LiteColors.textSecondary
        statusLabel.textAlignment = .center
        statusLabel.translatesAutoresizingMaskIntoConstraints = false
        contentView.addSubview(statusLabel)

        let footerBg = UIView()
        footerBg.backgroundColor = UIColor(hex: 0x06070B, alpha: 0.65)
        footerBg.translatesAutoresizingMaskIntoConstraints = false
        contentView.addSubview(footerBg)

        avatarView.translatesAutoresizingMaskIntoConstraints = false
        authorButton.setTitleColor(.white, for: .normal)
        authorButton.titleLabel?.font = .boldSystemFont(ofSize: 15)
        authorButton.addTarget(self, action: #selector(profileTapped), for: .touchUpInside)
        authorButton.translatesAutoresizingMaskIntoConstraints = false

        verifiedView.image = LiteIcons.icon(.verified, size: 15, color: LiteColors.verifiedBlue)
        verifiedView.translatesAutoresizingMaskIntoConstraints = false

        followButton.titleLabel?.font = .boldSystemFont(ofSize: 12)
        followButton.setTitleColor(.white, for: .normal)
        followButton.layer.cornerRadius = 12
        followButton.contentEdgeInsets = UIEdgeInsets(top: 5, left: 10, bottom: 5, right: 10)
        followButton.addTarget(self, action: #selector(followTapped), for: .touchUpInside)
        followButton.translatesAutoresizingMaskIntoConstraints = false

        captionLabel.font = .systemFont(ofSize: 14)
        captionLabel.textColor = .white
        captionLabel.numberOfLines = 3
        captionLabel.translatesAutoresizingMaskIntoConstraints = false

        tagsLabel.font = .systemFont(ofSize: 12, weight: .semibold)
        tagsLabel.textColor = LiteColors.electricCyan
        tagsLabel.numberOfLines = 1
        tagsLabel.translatesAutoresizingMaskIntoConstraints = false

        footerBg.addSubview(avatarView)
        footerBg.addSubview(authorButton)
        footerBg.addSubview(verifiedView)
        footerBg.addSubview(followButton)
        footerBg.addSubview(captionLabel)
        footerBg.addSubview(tagsLabel)

        likeButton.addTarget(self, action: #selector(likeTapped), for: .touchUpInside)
        commentButton.addTarget(self, action: #selector(commentTapped), for: .touchUpInside)
        shareButton.addTarget(self, action: #selector(shareTapped), for: .touchUpInside)
        muteButton.addTarget(self, action: #selector(muteTapped), for: .touchUpInside)

        [likeCountLabel, commentCountLabel].forEach {
            $0.font = .boldSystemFont(ofSize: 12)
            $0.textColor = .white
            $0.textAlignment = .center
        }

        let actionsStack = UIStackView(arrangedSubviews: [
            makeActionColumn(button: likeButton, label: likeCountLabel),
            makeActionColumn(button: commentButton, label: commentCountLabel),
            shareButton,
            muteButton
        ])
        actionsStack.axis = .vertical
        actionsStack.alignment = .center
        actionsStack.spacing = 14
        actionsStack.translatesAutoresizingMaskIntoConstraints = false
        contentView.addSubview(actionsStack)

        NSLayoutConstraint.activate([
            statusLabel.centerXAnchor.constraint(equalTo: contentView.centerXAnchor),
            statusLabel.centerYAnchor.constraint(equalTo: contentView.centerYAnchor),

            footerBg.leadingAnchor.constraint(equalTo: contentView.leadingAnchor),
            footerBg.trailingAnchor.constraint(equalTo: contentView.trailingAnchor),
            footerBg.bottomAnchor.constraint(equalTo: contentView.bottomAnchor),

            avatarView.topAnchor.constraint(equalTo: footerBg.topAnchor, constant: 12),
            avatarView.leadingAnchor.constraint(equalTo: footerBg.leadingAnchor, constant: 14),
            avatarView.widthAnchor.constraint(equalToConstant: 34),
            avatarView.heightAnchor.constraint(equalToConstant: 34),

            authorButton.centerYAnchor.constraint(equalTo: avatarView.centerYAnchor),
            authorButton.leadingAnchor.constraint(equalTo: avatarView.trailingAnchor, constant: 8),

            verifiedView.centerYAnchor.constraint(equalTo: avatarView.centerYAnchor),
            verifiedView.leadingAnchor.constraint(equalTo: authorButton.trailingAnchor, constant: 4),
            verifiedView.widthAnchor.constraint(equalToConstant: 15),
            verifiedView.heightAnchor.constraint(equalToConstant: 15),

            followButton.centerYAnchor.constraint(equalTo: avatarView.centerYAnchor),
            followButton.leadingAnchor.constraint(equalTo: verifiedView.trailingAnchor, constant: 8),

            captionLabel.topAnchor.constraint(equalTo: avatarView.bottomAnchor, constant: 8),
            captionLabel.leadingAnchor.constraint(equalTo: footerBg.leadingAnchor, constant: 14),
            captionLabel.trailingAnchor.constraint(equalTo: actionsStack.leadingAnchor, constant: -12),

            tagsLabel.topAnchor.constraint(equalTo: captionLabel.bottomAnchor, constant: 4),
            tagsLabel.leadingAnchor.constraint(equalTo: captionLabel.leadingAnchor),
            tagsLabel.trailingAnchor.constraint(equalTo: captionLabel.trailingAnchor),
            tagsLabel.bottomAnchor.constraint(equalTo: footerBg.bottomAnchor, constant: -16),

            actionsStack.trailingAnchor.constraint(equalTo: contentView.trailingAnchor, constant: -14),
            actionsStack.bottomAnchor.constraint(equalTo: footerBg.bottomAnchor, constant: -20),
            actionsStack.widthAnchor.constraint(equalToConstant: 52)
        ])
    }

    private func makeActionColumn(button: UIButton, label: UILabel) -> UIStackView {
        let s = UIStackView(arrangedSubviews: [button, label])
        s.axis = .vertical
        s.alignment = .center
        s.spacing = 2
        return s
    }

    func configure(
        video: LiteVideoPost,
        isOwnVideo: Bool,
        isFollowing: Bool,
        isLiked: Bool,
        isMuted: Bool,
        onLike: @escaping () -> Void,
        onFollow: @escaping () -> Void,
        onComment: @escaping () -> Void,
        onShare: @escaping () -> Void,
        onMuteToggle: @escaping () -> Void,
        onOpenProfile: @escaping () -> Void
    ) {
        self.currentVideo = video
        self.onLike = onLike
        self.onFollow = onFollow
        self.onComment = onComment
        self.onShare = onShare
        self.onMuteToggle = onMuteToggle
        self.onOpenProfile = onOpenProfile

        thumbView.image = LiteImageLoader.shared.imageFromBase64(video.thumbnailData, key: video.id)
        avatarView.configure(displayName: video.authorName, avatarUrl: video.authorAvatarUrl, size: 34)
        authorButton.setTitle("@\(video.authorUsername)", for: .normal)
        verifiedView.isHidden = !(video.authorVerified || LiteSocialRepository.isVerifiedUsername(video.authorUsername))
        followButton.isHidden = isOwnVideo
        followButton.setTitle(isFollowing ? "Takip Ediliyor" : "Takip Et", for: .normal)
        followButton.backgroundColor = isFollowing ? LiteColors.surfaceVariant : LiteColors.neonCoral

        captionLabel.text = video.caption
        tagsLabel.text = video.tags.map { "#\($0)" }.joined(separator: "  ")
        tagsLabel.isHidden = video.tags.isEmpty

        likeButton.setImage(LiteIcons.icon(.heart(filled: isLiked), size: 26, color: isLiked ? LiteColors.neonCoral : .white), for: .normal)
        likeCountLabel.text = "\(video.likesCount)"
        commentButton.setImage(LiteIcons.icon(.comment, size: 25, color: .white), for: .normal)
        commentCountLabel.text = "\(video.commentsCount)"
        shareButton.setImage(LiteIcons.icon(.share, size: 25, color: .white), for: .normal)
        muteButton.setImage(LiteIcons.icon(.volume(muted: isMuted), size: 22, color: .white), for: .normal)
        statusLabel.text = ""
    }

    func startPlayback(muted: Bool) {
        guard let video = currentVideo else { return }
        if !video.videoUrl.isEmpty, let url = URL(string: video.videoUrl) {
            attachAndPlay(url: url, muted: muted)
            return
        }
        statusLabel.text = "Yükleniyor..."
        LiteSocialRepository.shared.downloadAndCacheCloudVideo(videoId: video.id, expectedChunkCount: video.chunkCount) { [weak self] result in
            guard let self, self.currentVideo?.id == video.id else { return }
            switch result {
            case .success(let localUrl):
                self.statusLabel.text = ""
                self.attachAndPlay(url: localUrl, muted: muted)
            case .failure(let err):
                self.statusLabel.text = err.localizedDescription
            }
        }
    }

    private func attachAndPlay(url: URL, muted: Bool) {
        if player == nil {
            let avPlayer = AVPlayer(url: url)
            avPlayer.automaticallyWaitsToMinimizeStalling = true
            let layer = AVPlayerLayer(player: avPlayer)
            layer.videoGravity = .resizeAspectFill
            layer.frame = playerContainer.bounds
            playerContainer.layer.addSublayer(layer)
            self.player = avPlayer
            self.playerLayer = layer

            loopObserver = NotificationCenter.default.addObserver(
                forName: .AVPlayerItemDidPlayToEndTime,
                object: avPlayer.currentItem,
                queue: .main
            ) { [weak avPlayer] _ in
                avPlayer?.seek(to: .zero)
                avPlayer?.play()
            }
        }
        player?.isMuted = muted
        player?.play()
    }

    func stopPlayback() {
        if let loopObserver {
            NotificationCenter.default.removeObserver(loopObserver)
            self.loopObserver = nil
        }
        player?.pause()
        player?.replaceCurrentItem(with: nil)
        playerLayer?.removeFromSuperlayer()
        playerLayer = nil
        player = nil
    }

    @objc private func likeTapped() { onLike?() }
    @objc private func followTapped() { onFollow?() }
    @objc private func commentTapped() { onComment?() }
    @objc private func shareTapped() { onShare?() }
    @objc private func muteTapped() { onMuteToggle?() }
    @objc private func profileTapped() { onOpenProfile?() }
}
