import AVFoundation
import UIKit

final class LiteDiscoverViewController: UIViewController, UICollectionViewDataSource, UICollectionViewDelegate, UICollectionViewDelegateFlowLayout, UISearchBarDelegate {
    private let searchBar = UISearchBar()
    private let collectionView: UICollectionView
    private var query = ""

    private var filteredVideos: [LiteVideoPost] {
        let q = query.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        let videos = LiteAppState.shared.videos
        guard !q.isEmpty else { return videos }
        return videos.filter {
            $0.caption.lowercased().contains(q) ||
            $0.authorUsername.lowercased().contains(q) ||
            $0.tags.contains { $0.lowercased().contains(q) }
        }
    }

    init() {
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

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = LiteColors.bg
        title = "Keşfet"

        searchBar.barTintColor = LiteColors.surface
        searchBar.tintColor = LiteColors.neonCoral
        searchBar.placeholder = "Üretici veya video filtrele..."
        searchBar.delegate = self
        searchBar.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(searchBar)

        collectionView.backgroundColor = LiteColors.bg
        collectionView.dataSource = self
        collectionView.delegate = self
        collectionView.register(LiteVideoTileCell.self, forCellWithReuseIdentifier: "TileCell")
        collectionView.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(collectionView)

        NSLayoutConstraint.activate([
            searchBar.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor),
            searchBar.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            searchBar.trailingAnchor.constraint(equalTo: view.trailingAnchor),

            collectionView.topAnchor.constraint(equalTo: searchBar.bottomAnchor),
            collectionView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            collectionView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            collectionView.bottomAnchor.constraint(equalTo: view.safeAreaLayoutGuide.bottomAnchor)
        ])

        NotificationCenter.default.addObserver(self, selector: #selector(reload), name: .liteStateDidUpdate, object: nil)
    }

    @objc private func reload() {
        collectionView.reloadData()
    }

    func searchBar(_ searchBar: UISearchBar, textDidChange searchText: String) {
        query = searchText
        collectionView.reloadData()
    }

    func searchBarSearchButtonClicked(_ searchBar: UISearchBar) {
        searchBar.resignFirstResponder()
    }

    func collectionView(_ collectionView: UICollectionView, numberOfItemsInSection section: Int) -> Int {
        filteredVideos.count
    }

    func collectionView(_ collectionView: UICollectionView, layout collectionViewLayout: UICollectionViewLayout, sizeForItemAt indexPath: IndexPath) -> CGSize {
        let width = (collectionView.bounds.width - 34) / 2
        return CGSize(width: width, height: 210)
    }

    func collectionView(_ collectionView: UICollectionView, cellForItemAt indexPath: IndexPath) -> UICollectionViewCell {
        let cell = collectionView.dequeueReusableCell(withReuseIdentifier: "TileCell", for: indexPath) as! LiteVideoTileCell
        cell.configure(video: filteredVideos[indexPath.item])
        return cell
    }

    func collectionView(_ collectionView: UICollectionView, didSelectItemAt indexPath: IndexPath) {
        let player = LiteSingleVideoPlayerController(video: filteredVideos[indexPath.item])
        present(player, animated: true)
    }
}

final class LiteVideoTileCell: UICollectionViewCell {
    private let thumbView = UIImageView()
    private let captionLabel = UILabel()
    private let statsLabel = UILabel()
    let deleteButton = UIButton(type: .system)
    var onDelete: (() -> Void)?

    override init(frame: CGRect) {
        super.init(frame: frame)
        backgroundColor = LiteColors.surfaceVariant
        layer.cornerRadius = 14
        clipsToBounds = true

        thumbView.contentMode = .scaleAspectFill
        thumbView.frame = bounds
        thumbView.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        contentView.addSubview(thumbView)

        let overlay = UIView()
        overlay.backgroundColor = UIColor.black.withAlphaComponent(0.55)
        overlay.translatesAutoresizingMaskIntoConstraints = false
        contentView.addSubview(overlay)

        captionLabel.font = .boldSystemFont(ofSize: 12)
        captionLabel.textColor = .white
        captionLabel.numberOfLines = 2
        captionLabel.translatesAutoresizingMaskIntoConstraints = false
        overlay.addSubview(captionLabel)

        statsLabel.font = .systemFont(ofSize: 11)
        statsLabel.textColor = LiteColors.textSecondary
        statsLabel.translatesAutoresizingMaskIntoConstraints = false
        overlay.addSubview(statsLabel)

        deleteButton.setImage(LiteIcons.icon(.trash, size: 15, color: .white), for: .normal)
        deleteButton.backgroundColor = UIColor.black.withAlphaComponent(0.65)
        deleteButton.layer.cornerRadius = 14
        deleteButton.isHidden = true
        deleteButton.addTarget(self, action: #selector(deleteTapped), for: .touchUpInside)
        deleteButton.translatesAutoresizingMaskIntoConstraints = false
        contentView.addSubview(deleteButton)

        NSLayoutConstraint.activate([
            overlay.leadingAnchor.constraint(equalTo: contentView.leadingAnchor),
            overlay.trailingAnchor.constraint(equalTo: contentView.trailingAnchor),
            overlay.bottomAnchor.constraint(equalTo: contentView.bottomAnchor),

            captionLabel.topAnchor.constraint(equalTo: overlay.topAnchor, constant: 6),
            captionLabel.leadingAnchor.constraint(equalTo: overlay.leadingAnchor, constant: 8),
            captionLabel.trailingAnchor.constraint(equalTo: overlay.trailingAnchor, constant: -8),

            statsLabel.topAnchor.constraint(equalTo: captionLabel.bottomAnchor, constant: 2),
            statsLabel.leadingAnchor.constraint(equalTo: captionLabel.leadingAnchor),
            statsLabel.trailingAnchor.constraint(equalTo: captionLabel.trailingAnchor),
            statsLabel.bottomAnchor.constraint(equalTo: overlay.bottomAnchor, constant: -8),

            deleteButton.topAnchor.constraint(equalTo: contentView.topAnchor, constant: 8),
            deleteButton.trailingAnchor.constraint(equalTo: contentView.trailingAnchor, constant: -8),
            deleteButton.widthAnchor.constraint(equalToConstant: 28),
            deleteButton.heightAnchor.constraint(equalToConstant: 28)
        ])
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    @objc private func deleteTapped() { onDelete?() }

    func configure(video: LiteVideoPost, showDelete: Bool = false, onDelete: (() -> Void)? = nil) {
        thumbView.image = LiteImageLoader.shared.imageFromBase64(video.thumbnailData, key: video.id)
        captionLabel.text = video.caption.isEmpty ? "@\(video.authorUsername)" : video.caption
        statsLabel.text = "\(video.likesCount) beğeni · \(video.commentsCount) yorum"
        deleteButton.isHidden = !showDelete
        self.onDelete = onDelete
    }
}

final class LiteSingleVideoPlayerController: UIViewController {
    private let video: LiteVideoPost
    private var player: AVPlayer?
    private var playerLayer: AVPlayerLayer?

    init(video: LiteVideoPost) {
        self.video = video
        super.init(nibName: nil, bundle: nil)
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    deinit {
        player?.pause()
        player?.replaceCurrentItem(with: nil)
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .black

        let closeBtn = UIButton(type: .system)
        closeBtn.setImage(LiteIcons.icon(.close, size: 18, color: .white), for: .normal)
        closeBtn.backgroundColor = LiteColors.surface
        closeBtn.layer.cornerRadius = 20
        closeBtn.addTarget(self, action: #selector(closeTapped), for: .touchUpInside)
        closeBtn.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(closeBtn)

        NSLayoutConstraint.activate([
            closeBtn.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor, constant: 12),
            closeBtn.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 16),
            closeBtn.widthAnchor.constraint(equalToConstant: 40),
            closeBtn.heightAnchor.constraint(equalToConstant: 40)
        ])

        if !video.videoUrl.isEmpty, let url = URL(string: video.videoUrl) {
            play(url: url)
        } else {
            LiteSocialRepository.shared.downloadAndCacheCloudVideo(videoId: video.id, expectedChunkCount: video.chunkCount) { [weak self] res in
                if case .success(let localUrl) = res {
                    self?.play(url: localUrl)
                }
            }
        }
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        playerLayer?.frame = view.bounds
    }

    private func play(url: URL) {
        let p = AVPlayer(url: url)
        let l = AVPlayerLayer(player: p)
        l.videoGravity = .resizeAspect
        l.frame = view.bounds
        view.layer.insertSublayer(l, at: 0)
        self.player = p
        self.playerLayer = l
        p.play()
    }

    @objc private func closeTapped() {
        dismiss(animated: true)
    }
}
