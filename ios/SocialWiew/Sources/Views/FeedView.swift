import SwiftUI

enum FeedTabFilter: String, CaseIterable {
    case forYou = "Senin İçin"
    case following = "Takip"
}

struct FeedView: View {
    @ObservedObject var viewModel: SocialFeedViewModel
    let onOpenCreatorProfile: (String) -> Void
    let onNavigateToUpload: () -> Void
    let onNavigateToDiscover: () -> Void

    @State private var selectedTab: FeedTabFilter = .forYou
    @State private var isMuted = false
    @State private var activeVideoId: String?

    private var displayedVideos: [VideoPost] {
        switch selectedTab {
        case .forYou:
            return viewModel.videos
        case .following:
            return viewModel.videos.filter { viewModel.followingIds.contains($0.userId) }
        }
    }

    var body: some View {
        ZStack {
            Color(hex: 0x06070B).ignoresSafeArea()

            if displayedVideos.isEmpty {
                emptyState
            } else {
                ScrollView(.vertical) {
                    LazyVStack(spacing: 0) {
                        ForEach(displayedVideos) { video in
                            FeedReelCard(
                                video: video,
                                isActive: (activeVideoId ?? displayedVideos.first?.id) == video.id && viewModel.selectedVideoForComments == nil,
                                isMuted: isMuted,
                                isOwnVideo: video.userId == viewModel.currentUser?.uid,
                                isAuthorVerified: video.authorVerified || viewModel.verifiedUserIds.contains(video.userId) || SocialRepository.isVerifiedUsername(video.authorUsername),
                                isFollowing: viewModel.followingIds.contains(video.userId),
                                isLiked: viewModel.likedVideoIds.contains(video.id),
                                onToggleMute: { isMuted.toggle() },
                                onToggleLike: { viewModel.toggleLike(video: video) },
                                onToggleFollow: { viewModel.toggleFollow(targetUserId: video.userId) },
                                onOpenComments: { viewModel.openComments(for: video) },
                                onOpenCreator: { onOpenCreatorProfile(video.userId) },
                                onResolveCloudVideo: viewModel.resolveCloudVideoURL
                            )
                            .containerRelativeFrame([.horizontal, .vertical])
                            .id(video.id)
                        }
                    }
                    .scrollTargetLayout()
                }
                .scrollTargetBehavior(.paging)
                .scrollPosition(id: $activeVideoId)
                .ignoresSafeArea(edges: .top)
            }

            VStack {
                LiquidGlassGroup(spacing: 8) {
                    HStack(spacing: 6) {
                        ForEach(FeedTabFilter.allCases, id: \.self) { tab in
                            Button {
                                withAnimation(.spring(response: 0.3, dampingFraction: 0.8)) {
                                    selectedTab = tab
                                    activeVideoId = displayedVideos.first?.id
                                }
                            } label: {
                                Text(tab.rawValue)
                                    .font(.subheadline.weight(.semibold))
                                    .foregroundStyle(selectedTab == tab ? .white : .white.opacity(0.75))
                                    .padding(.horizontal, 14)
                                    .padding(.vertical, 8)
                                    .background(
                                        selectedTab == tab
                                            ? SocialWiewColors.neonCoral.opacity(0.85)
                                            : Color.clear,
                                        in: Capsule()
                                    )
                            }
                        }
                    }
                    .padding(4)
                    .liquidGlassCapsule(interactive: true)
                }
                .padding(.top, 12)
                Spacer()
            }
        }
    }

    private var emptyState: some View {
        VStack(spacing: 16) {
            Image(systemName: selectedTab == .following ? "person.2.slash" : "video.slash")
                .font(.system(size: 44))
                .foregroundStyle(SocialWiewColors.electricCyan)

            Text(selectedTab == .following ? "Henüz takip ettiğin üreticilerin videosu yok" : "Akışta henüz video yok")
                .font(.title3.bold())
                .foregroundStyle(.white)
                .multilineTextAlignment(.center)

            Text(selectedTab == .following ? "Keşfet sekmesinden yeni üreticiler takip edebilirsin." : "İlk videoyu paylaşarak topluluğu başlat.")
                .font(.subheadline)
                .foregroundStyle(.white.opacity(0.7))
                .multilineTextAlignment(.center)
                .padding(.horizontal, 32)

            Button(selectedTab == .following ? "Üreticileri Keşfet" : "Video Paylaş") {
                if selectedTab == .following {
                    onNavigateToDiscover()
                } else {
                    onNavigateToUpload()
                }
            }
            .buttonStyle(.borderedProminent)
            .tint(SocialWiewColors.neonCoral)
        }
    }
}

private struct FeedReelCard: View {
    let video: VideoPost
    let isActive: Bool
    let isMuted: Bool
    let isOwnVideo: Bool
    let isAuthorVerified: Bool
    let isFollowing: Bool
    let isLiked: Bool
    let onToggleMute: () -> Void
    let onToggleLike: () -> Void
    let onToggleFollow: () -> Void
    let onOpenComments: () -> Void
    let onOpenCreator: () -> Void
    let onResolveCloudVideo: (String, Int) async throws -> URL

    var body: some View {
        ZStack(alignment: .bottom) {
            VideoPlayerView(
                video: video,
                isActive: isActive,
                isMuted: isMuted,
                onResolveCloudVideo: onResolveCloudVideo
            )
            .frame(maxWidth: .infinity, maxHeight: .infinity)

            LinearGradient(
                colors: [.clear, .black.opacity(0.75)],
                startPoint: .center,
                endPoint: .bottom
            )
            .allowsHitTesting(false)

            HStack(alignment: .bottom, spacing: 12) {
                VStack(alignment: .leading, spacing: 8) {
                    HStack(spacing: 8) {
                        Button(action: onOpenCreator) {
                            HStack(spacing: 8) {
                                CreatorAvatarView(displayName: video.authorName, avatarUrl: video.authorAvatarUrl, size: 36)
                                Text("@\(video.authorUsername)")
                                    .font(.headline)
                                    .foregroundStyle(.white)
                                if isAuthorVerified {
                                    VerifiedBadgeView(size: 14)
                                }
                            }
                        }
                        if !isOwnVideo {
                            Button(action: onToggleFollow) {
                                Text(isFollowing ? "Takip Ediliyor" : "Takip Et")
                                    .font(.caption.bold())
                                    .padding(.horizontal, 12)
                                    .padding(.vertical, 6)
                                    .foregroundStyle(.white)
                                    .liquidGlassCapsule(
                                        tint: isFollowing ? nil : SocialWiewColors.neonCoral,
                                        interactive: true
                                    )
                            }
                        }
                    }

                    if !video.caption.isEmpty {
                        Text(video.caption)
                            .font(.subheadline)
                            .foregroundStyle(.white)
                            .lineLimit(3)
                    }

                    if !video.tags.isEmpty {
                        Text(video.tags.map { "#\($0)" }.joined(separator: "  "))
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(SocialWiewColors.electricCyan)
                    }
                }

                Spacer(minLength: 8)

                LiquidGlassGroup(spacing: 12) {
                    VStack(spacing: 12) {
                        Button(action: onToggleLike) {
                            VStack(spacing: 3) {
                                Image(systemName: isLiked ? "heart.fill" : "heart")
                                    .font(.title3)
                                    .foregroundStyle(isLiked ? SocialWiewColors.neonCoral : .white)
                                    .frame(width: 44, height: 44)
                                    .liquidGlass(
                                        in: Circle(),
                                        tint: isLiked ? SocialWiewColors.neonCoral : nil,
                                        interactive: true
                                    )
                                Text("\(video.likesCount)")
                                    .font(.caption2.monospacedDigit().bold())
                                    .foregroundStyle(.white)
                            }
                        }

                        Button(action: onOpenComments) {
                            VStack(spacing: 3) {
                                Image(systemName: "bubble.right.fill")
                                    .font(.title3)
                                    .foregroundStyle(.white)
                                    .frame(width: 44, height: 44)
                                    .liquidGlass(in: Circle(), interactive: true)
                                Text("\(video.commentsCount)")
                                    .font(.caption2.monospacedDigit().bold())
                                    .foregroundStyle(.white)
                            }
                        }

                        ShareLink(item: video.videoUrl.isEmpty ? "SocialWiew: \(video.caption)" : video.videoUrl) {
                            VStack(spacing: 3) {
                                Image(systemName: "arrowshape.turn.up.right.fill")
                                    .font(.title3)
                                    .foregroundStyle(.white)
                                    .frame(width: 44, height: 44)
                                    .liquidGlass(in: Circle(), interactive: true)
                                Text("Paylaş")
                                    .font(.caption2.bold())
                                    .foregroundStyle(.white)
                            }
                        }

                        Button(action: onToggleMute) {
                            Image(systemName: isMuted ? "speaker.slash.fill" : "speaker.wave.2.fill")
                                .font(.body)
                                .foregroundStyle(.white)
                                .frame(width: 42, height: 42)
                                .liquidGlass(in: Circle(), interactive: true)
                        }
                    }
                }
            }
            .padding(.horizontal, 16)
            .padding(.bottom, 24)
        }
    }
}
