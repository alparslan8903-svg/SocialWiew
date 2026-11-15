import SwiftUI

struct ProfileView: View {
    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.openURL) private var openURL
    @ObservedObject var viewModel: SocialFeedViewModel
    let userId: String
    let onOpenVideoPlayer: (VideoPost) -> Void

    @State private var showEditSheet = false
    @State private var showRepoSheet = false

    private var isOwnProfile: Bool {
        userId == viewModel.currentUser?.uid
    }

    private var profile: UserPublic? {
        if isOwnProfile {
            return viewModel.currentProfile ?? viewModel.creators.first(where: { $0.userId == userId })
        }
        return viewModel.creators.first(where: { $0.userId == userId })
    }

    private var userVideos: [VideoPost] {
        viewModel.videos.filter { $0.userId == userId }
    }

    private var isFollowing: Bool {
        viewModel.followingIds.contains(userId)
    }

    var body: some View {
        ScrollView {
            VStack(spacing: 20) {
                if let profile {
                    VStack(spacing: 12) {
                        CreatorAvatarView(displayName: profile.displayName, avatarUrl: profile.avatarUrl, size: 84)

                        HStack(spacing: 6) {
                            Text(profile.displayName)
                                .font(.title2.bold())
                                .foregroundStyle(SocialWiewColors.textPrimary(colorScheme))
                            if profile.isVerified || SocialRepository.isVerifiedUsername(profile.username) {
                                VerifiedBadgeView(size: 18)
                            }
                        }

                        Text("@\(profile.username)")
                            .font(.subheadline)
                            .foregroundStyle(SocialWiewColors.textSecondary(colorScheme))

                        if !profile.bio.isEmpty {
                            Text(profile.bio)
                                .font(.subheadline)
                                .multilineTextAlignment(.center)
                                .foregroundStyle(SocialWiewColors.textPrimary(colorScheme))
                                .padding(.horizontal, 24)
                        }

                        HStack(spacing: 28) {
                            statColumn(value: profile.videosCount, title: "Video")
                            statColumn(value: profile.followersCount, title: "Takipçi")
                            statColumn(value: profile.followingCount, title: "Takip")
                        }
                        .padding(.top, 4)

                        HStack(spacing: 12) {
                            if isOwnProfile {
                                Button("Profili Düzenle") {
                                    showEditSheet = true
                                }
                                .buttonStyle(.borderedProminent)
                                .tint(SocialWiewColors.primary(colorScheme))

                                Button("Çıkış Yap", role: .destructive) {
                                    viewModel.signOut()
                                }
                                .buttonStyle(.bordered)
                            } else {
                                Button(isFollowing ? "Takip Ediliyor" : "Takip Et") {
                                    viewModel.toggleFollow(targetUserId: userId)
                                }
                                .buttonStyle(.borderedProminent)
                                .tint(isFollowing ? SocialWiewColors.surfaceVariant(colorScheme) : SocialWiewColors.primary(colorScheme))

                                if viewModel.isAdmin {
                                    Button(profile.isVerified ? "Mavi Tiki Kaldır" : "Mavi Tik Ver") {
                                        viewModel.setUserVerified(targetUserId: userId, verified: !profile.isVerified)
                                    }
                                    .buttonStyle(.bordered)
                                }
                            }
                        }
                    }
                    .padding(.top, 12)
                } else {
                    ProgressView("Profil yükleniyor...")
                        .padding(.top, 48)
                }

                if isOwnProfile {
                    VStack(alignment: .leading, spacing: 12) {
                        Text("Görünüm Teması")
                            .font(.headline)
                            .foregroundStyle(SocialWiewColors.textPrimary(colorScheme))

                        Picker("Tema", selection: Binding(
                            get: { viewModel.themeMode },
                            set: { viewModel.setThemeMode($0) }
                        )) {
                            ForEach(AppThemeMode.allCases) { mode in
                                Text(mode.label).tag(mode)
                            }
                        }
                        .pickerStyle(.segmented)
                    }
                    .padding()
                    .liquidGlass(in: RoundedRectangle(cornerRadius: 20, style: .continuous))
                    .padding(.horizontal)

                    VStack(alignment: .leading, spacing: 10) {
                        HStack {
                            Text("GitHub Güncellemeleri")
                                .font(.headline)
                                .foregroundStyle(SocialWiewColors.textPrimary(colorScheme))
                            Spacer()
                            if viewModel.isAdmin {
                                Button("Depo Ayarla") {
                                    showRepoSheet = true
                                }
                                .font(.caption.bold())
                            }
                        }

                        if !viewModel.configuredGithubRepo.isEmpty {
                            Text("Depo: \(viewModel.configuredGithubRepo)")
                                .font(.caption)
                                .foregroundStyle(SocialWiewColors.textSecondary(colorScheme))
                        }

                        if let info = viewModel.githubUpdateInfo {
                            VStack(alignment: .leading, spacing: 4) {
                                Text(info.isNewerAvailable ? "Yeni sürüm mevcut: \(info.tagName)" : "Güncel sürüm: \(info.tagName)")
                                    .font(.subheadline.bold())
                                    .foregroundStyle(info.isNewerAvailable ? SocialWiewColors.secondary(colorScheme) : SocialWiewColors.textPrimary(colorScheme))
                                Text(info.releaseNotes)
                                    .font(.caption)
                                    .foregroundStyle(SocialWiewColors.textSecondary(colorScheme))
                                    .lineLimit(3)
                                if let url = URL(string: info.htmlUrl) {
                                    Button("GitHub'da İncele") {
                                        openURL(url)
                                    }
                                    .font(.caption.bold())
                                }
                            }
                        } else if let err = viewModel.githubUpdateError {
                            Text(err)
                                .font(.caption)
                                .foregroundStyle(SocialWiewColors.neonCoral)
                        }

                        Button {
                            viewModel.checkForGitHubUpdates()
                        } label: {
                            HStack {
                                if viewModel.githubUpdateLoading {
                                    ProgressView()
                                }
                                Text("Güncellemeleri Kontrol Et")
                            }
                            .frame(maxWidth: .infinity)
                        }
                        .buttonStyle(.bordered)
                    }
                    .padding()
                    .liquidGlass(in: RoundedRectangle(cornerRadius: 20, style: .continuous))
                    .padding(.horizontal)
                }

                VStack(alignment: .leading, spacing: 12) {
                    Text("Videolar (\(userVideos.count))")
                        .font(.headline)
                        .foregroundStyle(SocialWiewColors.textPrimary(colorScheme))
                        .padding(.horizontal)

                    if userVideos.isEmpty {
                        Text("Henüz paylaşılmış video yok.")
                            .font(.subheadline)
                            .foregroundStyle(SocialWiewColors.textSecondary(colorScheme))
                            .padding(.horizontal)
                    } else {
                        LazyVGrid(columns: [GridItem(.flexible(), spacing: 12), GridItem(.flexible(), spacing: 12)], spacing: 12) {
                            ForEach(userVideos) { video in
                                ZStack(alignment: .topTrailing) {
                                    VideoGridTile(video: video) {
                                        onOpenVideoPlayer(video)
                                    }
                                    if isOwnProfile {
                                        Button(role: .destructive) {
                                            viewModel.deleteVideo(videoId: video.id)
                                        } label: {
                                            Image(systemName: "trash.fill")
                                                .font(.caption)
                                                .foregroundStyle(.white)
                                                .padding(8)
                                                .liquidGlass(in: Circle(), interactive: true)
                                        }
                                        .padding(8)
                                    }
                                }
                            }
                        }
                        .padding(.horizontal)
                    }
                }
            }
            .padding(.bottom, 24)
        }
        .background(SocialWiewColors.bg(colorScheme))
        .navigationTitle(isOwnProfile ? "Profil" : "@\(profile?.username ?? "üretici")")
        .sheet(isPresented: $showEditSheet) {
            if let profile {
                EditProfileSheet(profile: profile) { name, username, bio in
                    try await viewModel.updateProfile(displayName: name, username: username, bio: bio)
                }
            }
        }
        .sheet(isPresented: $showRepoSheet) {
            ConfigureRepoSheet(initialRepo: viewModel.configuredGithubRepo) { repo in
                viewModel.configureGithubRepo(repo)
            }
        }
    }

    private func statColumn(value: Int64, title: String) -> some View {
        VStack(spacing: 2) {
            Text("\(value)")
                .font(.headline.monospacedDigit())
                .foregroundStyle(SocialWiewColors.textPrimary(colorScheme))
            Text(title)
                .font(.caption)
                .foregroundStyle(SocialWiewColors.textSecondary(colorScheme))
        }
    }
}

private struct EditProfileSheet: View {
    @Environment(\.dismiss) private var dismiss
    let profile: UserPublic
    let onSave: (String, String, String) async throws -> Void

    @State private var displayName: String
    @State private var username: String
    @State private var bio: String
    @State private var errorText: String?
    @State private var isSaving = false

    init(profile: UserPublic, onSave: @escaping (String, String, String) async throws -> Void) {
        self.profile = profile
        self.onSave = onSave
        _displayName = State(initialValue: profile.displayName)
        _username = State(initialValue: profile.username)
        _bio = State(initialValue: profile.bio)
    }

    var body: some View {
        NavigationStack {
            Form {
                TextField("Görünen Ad", text: $displayName)
                TextField("Kullanıcı Adı", text: $username)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                TextField("Biyografi", text: $bio, axis: .vertical)
                    .lineLimit(3...5)

                if let errorText {
                    Text(errorText)
                        .font(.caption)
                        .foregroundStyle(SocialWiewColors.neonCoral)
                }
            }
            .navigationTitle("Profili Düzenle")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("İptal") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Kaydet") {
                        isSaving = true
                        errorText = nil
                        Task {
                            do {
                                try await onSave(displayName, username, bio)
                                dismiss()
                            } catch {
                                errorText = error.localizedDescription
                            }
                            isSaving = false
                        }
                    }
                    .disabled(isSaving)
                }
            }
        }
    }
}

private struct ConfigureRepoSheet: View {
    @Environment(\.dismiss) private var dismiss
    @State private var repo: String
    let onSave: (String) -> Void

    init(initialRepo: String, onSave: @escaping (String) -> Void) {
        _repo = State(initialValue: initialRepo)
        self.onSave = onSave
    }

    var body: some View {
        NavigationStack {
            Form {
                TextField("kullanici/SocialWiew", text: $repo)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
            }
            .navigationTitle("GitHub Deposu")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("İptal") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Kaydet") {
                        onSave(repo)
                        dismiss()
                    }
                }
            }
        }
    }
}
