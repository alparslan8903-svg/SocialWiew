import UIKit
import FirebaseFirestore

final class LiteCommentsViewController: UIViewController, UITableViewDataSource, UITableViewDelegate, UICollectionViewDataSource, UICollectionViewDelegate {
    private let video: LiteVideoPost
    private var comments: [LiteVideoComment] = []
    private var listener: ListenerRegistration?

    private let tableView = UITableView()
    private let inputBar = UIView()
    private let gifToggleButton = UIButton(type: .system)
    private let textField = UITextField()
    private let sendButton = UIButton(type: .system)

    private let gifDrawer = UIView()
    private let gifSearchField = UITextField()
    private let gifCollection: UICollectionView
    private let tenorService = LiteTenorService()
    private var gifs: [LiteTenorGifItem] = []
    private var selectedGif: LiteTenorGifItem?
    private var gifDrawerHeightConstraint: NSLayoutConstraint?

    init(video: LiteVideoPost) {
        self.video = video
        let layout = UICollectionViewFlowLayout()
        layout.scrollDirection = .horizontal
        layout.itemSize = CGSize(width: 92, height: 82)
        layout.minimumInteritemSpacing = 8
        self.gifCollection = UICollectionView(frame: .zero, collectionViewLayout: layout)
        super.init(nibName: nil, bundle: nil)
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    deinit {
        listener?.remove()
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = LiteColors.bg
        title = "Yorumlar"

        navigationItem.leftBarButtonItem = UIBarButtonItem(
            title: "Kapat",
            style: .plain,
            target: self,
            action: #selector(closeTapped)
        )

        setupLayout()
        listener = LiteSocialRepository.shared.observeComments(videoId: video.id) { [weak self] list in
            self?.comments = list
            self?.tableView.reloadData()
        }
    }

    private func setupLayout() {
        tableView.backgroundColor = LiteColors.bg
        tableView.separatorColor = LiteColors.outline
        tableView.dataSource = self
        tableView.delegate = self
        tableView.register(LiteCommentCell.self, forCellReuseIdentifier: "CommentCell")
        tableView.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(tableView)

        gifDrawer.backgroundColor = LiteColors.surface
        gifDrawer.clipsToBounds = true
        gifDrawer.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(gifDrawer)

        gifSearchField.placeholder = "Tenor'da GIF ara..."
        gifSearchField.textColor = LiteColors.textPrimary
        gifSearchField.backgroundColor = LiteColors.surfaceVariant
        gifSearchField.layer.cornerRadius = 8
        gifSearchField.leftView = UIView(frame: CGRect(x: 0, y: 0, width: 10, height: 32))
        gifSearchField.leftViewMode = .always
        gifSearchField.attributedPlaceholder = NSAttributedString(string: "Tenor'da GIF ara...", attributes: [.foregroundColor: LiteColors.textSecondary])
        gifSearchField.returnKeyType = .search
        gifSearchField.addTarget(self, action: #selector(searchGifAction), for: .editingDidEndOnExit)
        gifSearchField.translatesAutoresizingMaskIntoConstraints = false
        gifDrawer.addSubview(gifSearchField)

        gifCollection.backgroundColor = .clear
        gifCollection.dataSource = self
        gifCollection.delegate = self
        gifCollection.register(LiteGifCell.self, forCellWithReuseIdentifier: "GifCell")
        gifCollection.translatesAutoresizingMaskIntoConstraints = false
        gifDrawer.addSubview(gifCollection)

        inputBar.backgroundColor = LiteColors.surface
        inputBar.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(inputBar)

        gifToggleButton.setTitle("GIF", for: .normal)
        gifToggleButton.setTitleColor(LiteColors.textPrimary, for: .normal)
        gifToggleButton.backgroundColor = LiteColors.surfaceVariant
        gifToggleButton.titleLabel?.font = .boldSystemFont(ofSize: 12)
        gifToggleButton.layer.cornerRadius = 8
        gifToggleButton.addTarget(self, action: #selector(toggleGifDrawer), for: .touchUpInside)
        gifToggleButton.translatesAutoresizingMaskIntoConstraints = false
        inputBar.addSubview(gifToggleButton)

        textField.placeholder = "Yorum yaz..."
        textField.textColor = LiteColors.textPrimary
        textField.backgroundColor = LiteColors.surfaceVariant
        textField.layer.cornerRadius = 10
        textField.leftView = UIView(frame: CGRect(x: 0, y: 0, width: 10, height: 34))
        textField.leftViewMode = .always
        textField.attributedPlaceholder = NSAttributedString(string: "Yorum yaz...", attributes: [.foregroundColor: LiteColors.textSecondary])
        textField.translatesAutoresizingMaskIntoConstraints = false
        inputBar.addSubview(textField)

        sendButton.setTitle("Gönder", for: .normal)
        sendButton.setTitleColor(LiteColors.neonCoral, for: .normal)
        sendButton.titleLabel?.font = .boldSystemFont(ofSize: 14)
        sendButton.addTarget(self, action: #selector(sendTapped), for: .touchUpInside)
        sendButton.translatesAutoresizingMaskIntoConstraints = false
        inputBar.addSubview(sendButton)

        let drawerHeight = gifDrawer.heightAnchor.constraint(equalToConstant: 0)
        gifDrawerHeightConstraint = drawerHeight

        NSLayoutConstraint.activate([
            tableView.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor),
            tableView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            tableView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            tableView.bottomAnchor.constraint(equalTo: gifDrawer.topAnchor),

            gifDrawer.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            gifDrawer.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            gifDrawer.bottomAnchor.constraint(equalTo: inputBar.topAnchor),
            drawerHeight,

            gifSearchField.topAnchor.constraint(equalTo: gifDrawer.topAnchor, constant: 8),
            gifSearchField.leadingAnchor.constraint(equalTo: gifDrawer.leadingAnchor, constant: 12),
            gifSearchField.trailingAnchor.constraint(equalTo: gifDrawer.trailingAnchor, constant: -12),
            gifSearchField.heightAnchor.constraint(equalToConstant: 34),

            gifCollection.topAnchor.constraint(equalTo: gifSearchField.bottomAnchor, constant: 8),
            gifCollection.leadingAnchor.constraint(equalTo: gifDrawer.leadingAnchor, constant: 12),
            gifCollection.trailingAnchor.constraint(equalTo: gifDrawer.trailingAnchor, constant: -12),
            gifCollection.bottomAnchor.constraint(equalTo: gifDrawer.bottomAnchor, constant: -8),

            inputBar.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            inputBar.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            inputBar.bottomAnchor.constraint(equalTo: view.safeAreaLayoutGuide.bottomAnchor),
            inputBar.heightAnchor.constraint(equalToConstant: 56),

            gifToggleButton.leadingAnchor.constraint(equalTo: inputBar.leadingAnchor, constant: 12),
            gifToggleButton.centerYAnchor.constraint(equalTo: inputBar.centerYAnchor),
            gifToggleButton.widthAnchor.constraint(equalToConstant: 44),
            gifToggleButton.heightAnchor.constraint(equalToConstant: 34),

            textField.leadingAnchor.constraint(equalTo: gifToggleButton.trailingAnchor, constant: 8),
            textField.centerYAnchor.constraint(equalTo: inputBar.centerYAnchor),
            textField.heightAnchor.constraint(equalToConstant: 36),

            sendButton.leadingAnchor.constraint(equalTo: textField.trailingAnchor, constant: 8),
            sendButton.trailingAnchor.constraint(equalTo: inputBar.trailingAnchor, constant: -12),
            sendButton.centerYAnchor.constraint(equalTo: inputBar.centerYAnchor)
        ])
    }

    @objc private func closeTapped() {
        dismiss(animated: true)
    }

    @objc private func toggleGifDrawer() {
        let opening = (gifDrawerHeightConstraint?.constant ?? 0) == 0
        gifDrawerHeightConstraint?.constant = opening ? 140 : 0
        gifToggleButton.backgroundColor = opening ? LiteColors.neonCoral : LiteColors.surfaceVariant
        UIView.animate(withDuration: 0.2) { self.view.layoutIfNeeded() }
        if opening && gifs.isEmpty {
            loadTenor("alkış")
        }
    }

    @objc private func searchGifAction() {
        loadTenor(gifSearchField.text ?? "")
    }

    private func loadTenor(_ query: String) {
        tenorService.loadGifs(query: query) { [weak self] result in
            guard let self else { return }
            switch result {
            case .success(let items):
                self.gifs = items
                self.gifCollection.reloadData()
            case .failure(let err):
                LiteToast.show(err.localizedDescription, in: self.view)
            }
        }
    }

    @objc private func sendTapped() {
        guard let author = LiteAppState.shared.currentProfile else {
            LiteToast.show("Profil yükleniyor...", in: view)
            return
        }
        let text = textField.text ?? ""
        LiteSocialRepository.shared.addComment(videoId: video.id, author: author, text: text, gif: selectedGif) { [weak self] err in
            guard let self else { return }
            if let err {
                LiteToast.show(err.localizedDescription, in: self.view)
            } else {
                self.textField.text = ""
                self.selectedGif = nil
                self.gifDrawerHeightConstraint?.constant = 0
                self.gifToggleButton.backgroundColor = LiteColors.surfaceVariant
            }
        }
    }

    func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int {
        comments.count
    }

    func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        let cell = tableView.dequeueReusableCell(withIdentifier: "CommentCell", for: indexPath) as! LiteCommentCell
        let comment = comments[indexPath.row]
        let isMine = comment.userId == LiteSocialRepository.shared.auth.currentUser?.uid
        cell.configure(comment: comment, isMine: isMine) { [weak self] in
            LiteSocialRepository.shared.deleteComment(videoId: self?.video.id ?? "", commentId: comment.id)
        }
        return cell
    }

    func collectionView(_ collectionView: UICollectionView, numberOfItemsInSection section: Int) -> Int {
        gifs.count
    }

    func collectionView(_ collectionView: UICollectionView, cellForItemAt indexPath: IndexPath) -> UICollectionViewCell {
        let cell = collectionView.dequeueReusableCell(withReuseIdentifier: "GifCell", for: indexPath) as! LiteGifCell
        cell.configure(urlString: gifs[indexPath.item].previewUrl)
        return cell
    }

    func collectionView(_ collectionView: UICollectionView, didSelectItemAt indexPath: IndexPath) {
        selectedGif = gifs[indexPath.item]
        gifDrawerHeightConstraint?.constant = 0
        gifToggleButton.backgroundColor = LiteColors.electricCyan
        LiteToast.show("GIF seçildi: \(gifs[indexPath.item].title)", in: view)
    }
}

private final class LiteCommentCell: UITableViewCell {
    private let avatarView = LiteAvatarView()
    private let authorLabel = UILabel()
    private let bodyLabel = UILabel()
    private let gifPreview = UIImageView()
    private let deleteButton = UIButton(type: .system)
    private var onDelete: (() -> Void)?
    private var gifTask: URLSessionDataTask?

    override init(style: UITableViewCell.CellStyle, reuseIdentifier: String?) {
        super.init(style: style, reuseIdentifier: reuseIdentifier)
        backgroundColor = LiteColors.bg
        selectionStyle = .none

        avatarView.translatesAutoresizingMaskIntoConstraints = false
        contentView.addSubview(avatarView)

        authorLabel.font = .boldSystemFont(ofSize: 13)
        authorLabel.textColor = LiteColors.textPrimary
        authorLabel.translatesAutoresizingMaskIntoConstraints = false
        contentView.addSubview(authorLabel)

        deleteButton.setImage(LiteIcons.icon(.trash, size: 16, color: LiteColors.neonCoral), for: .normal)
        deleteButton.addTarget(self, action: #selector(deleteTapped), for: .touchUpInside)
        deleteButton.translatesAutoresizingMaskIntoConstraints = false
        contentView.addSubview(deleteButton)

        bodyLabel.font = .systemFont(ofSize: 14)
        bodyLabel.textColor = LiteColors.textPrimary
        bodyLabel.numberOfLines = 0
        bodyLabel.translatesAutoresizingMaskIntoConstraints = false
        contentView.addSubview(bodyLabel)

        gifPreview.contentMode = .scaleAspectFill
        gifPreview.layer.cornerRadius = 8
        gifPreview.clipsToBounds = true
        gifPreview.translatesAutoresizingMaskIntoConstraints = false
        contentView.addSubview(gifPreview)

        NSLayoutConstraint.activate([
            avatarView.topAnchor.constraint(equalTo: contentView.topAnchor, constant: 10),
            avatarView.leadingAnchor.constraint(equalTo: contentView.leadingAnchor, constant: 14),
            avatarView.widthAnchor.constraint(equalToConstant: 32),
            avatarView.heightAnchor.constraint(equalToConstant: 32),

            authorLabel.topAnchor.constraint(equalTo: avatarView.topAnchor),
            authorLabel.leadingAnchor.constraint(equalTo: avatarView.trailingAnchor, constant: 10),

            deleteButton.centerYAnchor.constraint(equalTo: authorLabel.centerYAnchor),
            deleteButton.trailingAnchor.constraint(equalTo: contentView.trailingAnchor, constant: -14),

            bodyLabel.topAnchor.constraint(equalTo: authorLabel.bottomAnchor, constant: 4),
            bodyLabel.leadingAnchor.constraint(equalTo: authorLabel.leadingAnchor),
            bodyLabel.trailingAnchor.constraint(equalTo: contentView.trailingAnchor, constant: -14),

            gifPreview.topAnchor.constraint(equalTo: bodyLabel.bottomAnchor, constant: 6),
            gifPreview.leadingAnchor.constraint(equalTo: authorLabel.leadingAnchor),
            gifPreview.widthAnchor.constraint(equalToConstant: 140),
            gifPreview.heightAnchor.constraint(lessThanOrEqualToConstant: 100),
            gifPreview.bottomAnchor.constraint(equalTo: contentView.bottomAnchor, constant: -10)
        ])
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    @objc private func deleteTapped() {
        onDelete?()
    }

    func configure(comment: LiteVideoComment, isMine: Bool, onDelete: @escaping () -> Void) {
        gifTask?.cancel()
        gifTask = nil
        gifPreview.image = nil
        self.onDelete = onDelete

        avatarView.configure(displayName: comment.authorName, avatarUrl: comment.authorAvatarUrl, size: 32)
        authorLabel.text = "@\(comment.authorUsername)"
        bodyLabel.text = comment.text
        deleteButton.isHidden = !isMine

        let gifSrc = comment.gifPreviewUrl.isEmpty ? comment.gifUrl : comment.gifPreviewUrl
        gifPreview.isHidden = gifSrc.isEmpty
        if !gifSrc.isEmpty {
            gifTask = LiteImageLoader.shared.loadURL(gifSrc) { [weak self] img in
                self?.gifPreview.image = img
            }
        }
    }
}

private final class LiteGifCell: UICollectionViewCell {
    private let imageView = UIImageView()
    private var task: URLSessionDataTask?

    override init(frame: CGRect) {
        super.init(frame: frame)
        backgroundColor = LiteColors.surfaceVariant
        layer.cornerRadius = 8
        clipsToBounds = true
        imageView.contentMode = .scaleAspectFill
        imageView.frame = bounds
        imageView.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        contentView.addSubview(imageView)
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    func configure(urlString: String) {
        task?.cancel()
        imageView.image = nil
        task = LiteImageLoader.shared.loadURL(urlString) { [weak self] img in
            self?.imageView.image = img
        }
    }
}
