import SwiftUI

enum SearchScope: String, CaseIterable {
    case all = "Tümü"
    case creators = "Üreticiler"
    case videos = "Trend Videolar"
}

struct SearchView: View {
    @Environment(\.colorScheme) private var colorScheme
    @ObservedObject var viewModel: SocialFeedViewModel
    let onOpenCreatorProfile: (String) -> Void
    let onOpenVideoPlayer: (VideoPost) -> Void

    @State private var query = ""
    @State private var scope: SearchScope = .all
    @State private var recentQueries: [String] = UserDefaults.standard.stringArray(forKey: "socialwiew_recent_searches") ?? []

    private var trendingTags: [String] {
        var counts: [String: Int] = [:]
        for video in viewModel.videos {
            for tag in video.tags where !tag.isEmpty {
                counts[tag, default: 0] += 1
            }
        }
        return counts.sorted { $0.value > $1.value }.prefix(10).map(\.key)
    }

    private var matchingCreators: [UserPublic] {
        let q = query.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        guard !q.isEmpty else { return viewModel.creators }
        return viewModel.creators.filter {
            $0.displayName.lowercased().contains(q) ||
            $0.username.lowercased().contains(q) ||
            $0.bio.lowercased().contains(q)
        }
    }

    private var matchingVideos: [VideoPost] {
        let q = query.trimmingCharacters(in: .whitespacesAndNewlines).lowercased().replacingOccurrences(of: "#", with: "")
        guard !q.isEmpty else {
            return viewModel.videos.sorted { $0.likesCount > $1.likesCount }
        }
        return viewModel.videos.filter {
            $0.caption.lowercased().contains(q) ||
            $0.authorName.lowercased().contains(q) ||
            $0.authorUsername.lowercased().contains(q) ||
            $0.tags.contains { $0.lowercased().contains(q) }
        }
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    Picker("Kapsam", selection: $scope) {
                        ForEach(SearchScope.allCases, id: \.self) { item in
                            Text(item.rawValue).tag(item)
                        }
                    }
                    .pickerStyle(.segmented)
                    .padding(.horizontal)

                    if !trendingTags.isEmpty {
                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: 8) {
                                ForEach(trendingTags, id: \.self) { tag in
                                    Button("#\(tag)") {
                                        query = tag
                                        saveRecent(tag)
                                    }
                                    .font(.caption.bold())
                                    .padding(.horizontal, 12)
                                    .padding(.vertical, 6)
                                    .background(SocialWiewColors.surfaceVariant(colorScheme))
                                    .foregroundStyle(SocialWiewColors.secondary(colorScheme))
                                    .clipShape(Capsule())
                                }
                            }
                            .padding(.horizontal)
                        }
                    }

                    if query.isEmpty && !recentQueries.isEmpty {
                        VStack(alignment: .leading, spacing: 8) {
                            HStack {
                                Text("Son Aramalar")
                                    .font(.subheadline.bold())
                                    .foregroundStyle(SocialWiewColors.textPrimary(colorScheme))
                                Spacer()
                                Button("Temizle") {
                                    recentQueries.removeAll()
                                    UserDefaults.standard.removeObject(forKey: "socialwiew_recent_searches")
                                }
                                .font(.caption)
                            }
                            ForEach(recentQueries, id: \.self) { item in
                                Button {
                                    query = item
                                } label: {
                                    Label(item, systemImage: "clock.arrow.circlepath")
                                        .font(.subheadline)
                                        .foregroundStyle(SocialWiewColors.textSecondary(colorScheme))
                                }
                            }
                        }
                        .padding(.horizontal)
                    }

                    if scope == .all || scope == .creators {
                        VStack(alignment: .leading, spacing: 10) {
                            Text("Üreticiler")
                                .font(.headline)
                                .padding(.horizontal)

                            ForEach(matchingCreators) { creator in
                                Button {
                                    onOpenCreatorProfile(creator.userId)
                                } label: {
                                    HStack(spacing: 12) {
                                        CreatorAvatarView(displayName: creator.displayName, avatarUrl: creator.avatarUrl, size: 42)
                                        VStack(alignment: .leading, spacing: 2) {
                                            HStack(spacing: 4) {
                                                Text(creator.displayName)
                                                    .font(.subheadline.bold())
                                                    .foregroundStyle(SocialWiewColors.textPrimary(colorScheme))
                                                if creator.isVerified || SocialRepository.isVerifiedUsername(creator.username) {
                                                    VerifiedBadgeView(size: 13)
                                                }
                                            }
                                            Text("@\(creator.username) · \(creator.followersCount) takipçi")
                                                .font(.caption)
                                                .foregroundStyle(SocialWiewColors.textSecondary(colorScheme))
                                        }
                                        Spacer()
                                        Image(systemName: "chevron.right")
                                            .font(.caption)
                                            .foregroundStyle(SocialWiewColors.textSecondary(colorScheme))
                                    }
                                    .padding(12)
                                    .background(SocialWiewColors.surface(colorScheme))
                                    .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                                }
                                .padding(.horizontal)
                            }
                        }
                    }

                    if scope == .all || scope == .videos {
                        VStack(alignment: .leading, spacing: 10) {
                            Text("Trend Videolar")
                                .font(.headline)
                                .padding(.horizontal)

                            LazyVGrid(columns: [GridItem(.flexible(), spacing: 12), GridItem(.flexible(), spacing: 12)], spacing: 12) {
                                ForEach(matchingVideos) { video in
                                    VideoGridTile(video: video) {
                                        onOpenVideoPlayer(video)
                                    }
                                }
                            }
                            .padding(.horizontal)
                        }
                    }
                }
                .padding(.vertical, 12)
            }
            .background(SocialWiewColors.bg(colorScheme))
            .navigationTitle("Ara ve Keşfet")
            .searchable(text: $query, prompt: "Üretici, @kullanıcı veya #etiket ara...")
            .onSubmit(of: .search) {
                saveRecent(query)
            }
        }
    }

    private func saveRecent(_ term: String) {
        let clean = term.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !clean.isEmpty else { return }
        recentQueries.removeAll { $0.caseInsensitiveCompare(clean) == .orderedSame }
        recentQueries.insert(clean, at: 0)
        if recentQueries.count > 8 { recentQueries = Array(recentQueries.prefix(8)) }
        UserDefaults.standard.set(recentQueries, forKey: "socialwiew_recent_searches")
    }
}
