import UIKit

final class LiteSearchViewController: UIViewController, UISearchBarDelegate, UITableViewDataSource, UITableViewDelegate {
    private let searchBar = UISearchBar()
    private let scopeControl = UISegmentedControl(items: ["Üreticiler", "Videolar"])
    private let tableView = UITableView()
    private var query = ""

    private var matchingCreators: [LiteUserPublic] {
        let q = query.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        let creators = LiteAppState.shared.creators
        guard !q.isEmpty else { return creators }
        return creators.filter {
            $0.displayName.lowercased().contains(q) || $0.username.lowercased().contains(q)
        }
    }

    private var matchingVideos: [LiteVideoPost] {
        let q = query.trimmingCharacters(in: .whitespacesAndNewlines).lowercased().replacingOccurrences(of: "#", with: "")
        let videos = LiteAppState.shared.videos
        guard !q.isEmpty else { return videos.sorted { $0.likesCount > $1.likesCount } }
        return videos.filter {
            $0.caption.lowercased().contains(q) ||
            $0.authorUsername.lowercased().contains(q) ||
            $0.tags.contains { $0.lowercased().contains(q) }
        }
    }

    deinit {
        NotificationCenter.default.removeObserver(self)
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = LiteColors.bg
        title = "Ara"

        searchBar.barTintColor = LiteColors.surface
        searchBar.tintColor = LiteColors.neonCoral
        searchBar.placeholder = "Üretici, @kullanıcı veya #etiket ara..."
        searchBar.delegate = self
        searchBar.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(searchBar)

        scopeControl.selectedSegmentIndex = 0
        scopeControl.tintColor = LiteColors.neonCoral
        scopeControl.backgroundColor = LiteColors.surface
        scopeControl.setTitleTextAttributes([.foregroundColor: LiteColors.textPrimary], for: .normal)
        scopeControl.setTitleTextAttributes([.foregroundColor: UIColor.white], for: .selected)
        scopeControl.addTarget(self, action: #selector(reload), for: .valueChanged)
        scopeControl.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(scopeControl)

        tableView.backgroundColor = LiteColors.bg
        tableView.separatorColor = LiteColors.outline
        tableView.dataSource = self
        tableView.delegate = self
        tableView.register(UITableViewCell.self, forCellReuseIdentifier: "RowCell")
        tableView.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(tableView)

        NSLayoutConstraint.activate([
            searchBar.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor),
            searchBar.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            searchBar.trailingAnchor.constraint(equalTo: view.trailingAnchor),

            scopeControl.topAnchor.constraint(equalTo: searchBar.bottomAnchor, constant: 8),
            scopeControl.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 16),
            scopeControl.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -16),

            tableView.topAnchor.constraint(equalTo: scopeControl.bottomAnchor, constant: 8),
            tableView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            tableView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            tableView.bottomAnchor.constraint(equalTo: view.safeAreaLayoutGuide.bottomAnchor)
        ])

        NotificationCenter.default.addObserver(self, selector: #selector(reload), name: .liteStateDidUpdate, object: nil)
    }

    @objc private func reload() {
        tableView.reloadData()
    }

    func searchBar(_ searchBar: UISearchBar, textDidChange searchText: String) {
        query = searchText
        tableView.reloadData()
    }

    func searchBarSearchButtonClicked(_ searchBar: UISearchBar) {
        searchBar.resignFirstResponder()
    }

    func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int {
        scopeControl.selectedSegmentIndex == 0 ? matchingCreators.count : matchingVideos.count
    }

    func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        let cell = tableView.dequeueReusableCell(withIdentifier: "RowCell", for: indexPath)
        cell.backgroundColor = LiteColors.surface
        cell.textLabel?.textColor = LiteColors.textPrimary
        cell.textLabel?.numberOfLines = 2
        if scopeControl.selectedSegmentIndex == 0 {
            let c = matchingCreators[indexPath.row]
            cell.textLabel?.text = "\(c.displayName)  (@\(c.username)) · \(c.followersCount) takipçi"
        } else {
            let v = matchingVideos[indexPath.row]
            cell.textLabel?.text = "\(v.caption.isEmpty ? "@\(v.authorUsername)" : v.caption) · \(v.likesCount) beğeni"
        }
        return cell
    }

    func tableView(_ tableView: UITableView, didSelectRowAt indexPath: IndexPath) {
        tableView.deselectRow(at: indexPath, animated: true)
        if scopeControl.selectedSegmentIndex == 0 {
            let creator = matchingCreators[indexPath.row]
            navigationController?.pushViewController(LiteProfileViewController(userId: creator.userId), animated: true)
        } else {
            let video = matchingVideos[indexPath.row]
            present(LiteSingleVideoPlayerController(video: video), animated: true)
        }
    }
}
