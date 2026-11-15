import SwiftUI

struct DiscoverView: View {
    @Environment(\.colorScheme) private var colorScheme
    @ObservedObject var viewModel: SocialFeedViewModel
    let onOpenCreatorProfile: (String) -> Void
    let onOpenVideoPlayer: (VideoPost) -> Void

    @State private var query = ""

    private var filteredCreators: [UserPublic] {
        let q = query.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        guard !q.isEmpty else { return viewModel.creators }
        return viewModel.creators.filter {
            $0.displayName.lowercased().contains(q) || $0.username.lowercased().contains(q)
        }
    }

    private var filteredVideos: [VideoPost] {
        let q = query.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        guard !q.isEmpty else { return viewModel.videos }
        return viewModel.videos.filter {
            $0.caption.lowercased().contains(q) ||
            $0.authorUsername.lowercased().contains(q) ||
            $0.tags.contains { $0.lowercased().contains(q) }
        }
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    VStack(alignment: .leading, spacing: 10) {
                        Text("Yeni İçerik Üreticileri")
                            .font(.headline)
                            .foregroundStyle(SocialWiewColors.textPrimary(colorScheme))
                            .padding(.horizontal)

                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: 12) {
                                ForEach(filteredCreators) { creator in
                                    CreatorCard(
                                        creator: creator,
                                        isCurrentUser: creator.userId == viewModel.currentUser?.uid,
                                        isFollowing: viewModel.followingIds.contains(creator.userId),
                                        onTapProfile: { onOpenCreatorProfile(creator.userId) },
                                        onToggleFollow: { viewModel.toggleFollow(targetUserId: creator.userId) }
                                    )
                                }
                            }
                            .padding(.horizontal)
                        }
                    }

                    VStack(alignment: .leading, spacing: 12) {
                        Text("Topluluk Videoları")
                            .font(.headline)
                            .foregroundStyle(SocialWiewColors.textPrimary(colorScheme))
                            .padding(.horizontal)

                        LazyVGrid(columns: [GridItem(.flexible(), spacing: 12), GridItem(.flexible(), spacing: 12)], spacing: 12) {
                            ForEach(filteredVideos) { video in
                                VideoGridTile(video: video) {
                                    onOpenVideoPlayer(video)
                                }
                            }
                        }
                        .padding(.horizontal)
                    }
                }
                .padding(.vertical, 12)
            }
            .background(SocialWiewColors.bg(colorScheme))
            .navigationTitle("Keşfet")
            .searchable(text: $query, prompt: "Üretici veya video filtrele...")
        }
    }
}

private struct CreatorCard: View {
    @Environment(\.colorScheme) private var colorScheme
    let creator: UserPublic
    let isCurrentUser: Bool
    let isFollowing: Bool
    let onTapProfile: () -> Void
    let onToggleFollow: () -> Void

    var body: some View {
        VStack(spacing: 8) {
            Button(action: onTapProfile) {
                VStack(spacing: 6) {
                    CreatorAvatarView(displayName: creator.displayName, avatarUrl: creator.avatarUrl, size: 54)
                    HStack(spacing: 4) {
                        Text(creator.displayName)
                            .font(.subheadline.bold())
                            .foregroundStyle(SocialWiewColors.textPrimary(colorScheme))
                            .lineLimit(1)
                        if creator.isVerified || SocialRepository.isVerifiedUsername(creator.username) {
                            VerifiedBadgeView(size: 13)
                        }
                    }
                    Text("@\(creator.username) · \(creator.followersCount) takipçi")
                        .font(.caption2)
                        .foregroundStyle(SocialWiewColors.textSecondary(colorScheme))
                        .lineLimit(1)
                }
            }

            if !isCurrentUser {
                Button(action: onToggleFollow) {
                    Text(isFollowing ? "Takip Ediliyor" : "Takip Et")
                        .font(.caption.bold())
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 6)
                        .background(isFollowing ? SocialWiewColors.surfaceVariant(colorScheme) : SocialWiewColors.primary(colorScheme))
                        .foregroundStyle(isFollowing ? SocialWiewColors.textPrimary(colorScheme) : .white)
                        .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
                }
            }
        }
        .padding(12)
        .frame(width: 164)
        .background(SocialWiewColors.surface(colorScheme))
        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .stroke(SocialWiewColors.outline(colorScheme), lineWidth: 1)
        )
    }
}

struct VideoGridTile: View {
    @Environment(\.colorScheme) private var colorScheme
    let video: VideoPost
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            ZStack(alignment: .bottomLeading) {
                RoundedRectangle(cornerRadius: 16, style: .continuous)
                    .fill(SocialWiewColors.surfaceVariant(colorScheme))

                if let image = decodeThumb(video.thumbnailData) {
                    Image(uiImage: image)
                        .resizable()
                        .scaledToFill()
                } else {
                    Image(systemName: "play.circle.fill")
                        .font(.largeTitle)
                        .foregroundStyle(SocialWiewColors.primary(colorScheme))
                }

                LinearGradient(colors: [.clear, .black.opacity(0.75)], startPoint: .center, endPoint: .bottom)

                VStack(alignment: .leading, spacing: 2) {
                    Text(video.caption.isEmpty ? "@\(video.authorUsername)" : video.caption)
                        .font(.caption.bold())
                        .foregroundStyle(.white)
                        .lineLimit(2)
                    Text("\(video.likesCount) beğeni · \(video.commentsCount) yorum")
                        .font(.caption2.monospacedDigit())
                        .foregroundStyle(.white.opacity(0.8))
                }
                .padding(10)
            }
            .frame(height: 220)
            .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
        }
    }

    private func decodeThumb(_ raw: String) -> UIImage? {
        guard !raw.isEmpty else { return nil }
        let cleaned = raw.components(separatedBy: ",").last ?? raw
        guard let data = Data(base64Encoded: cleaned, options: .ignoreUnknownCharacters) else { return nil }
        return UIImage(data: data)
    }
}
