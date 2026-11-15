import SwiftUI

struct CommentsSheetView: View {
    @Environment(\.colorScheme) private var colorScheme
    @ObservedObject var viewModel: SocialFeedViewModel
    let video: VideoPost

    @State private var commentText = ""
    @State private var showGifPicker = false
    @State private var gifQuery = ""
    @State private var selectedGif: TenorGifItem?

    private let moodChips = ["alkış", "kahkaha", "aşk", "dans", "harika", "şaşkın"]

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                if viewModel.comments.isEmpty {
                    Spacer()
                    Text("Henüz yorum yok. İlk yorumu veya GIF'i sen gönder!")
                        .font(.subheadline)
                        .foregroundStyle(SocialWiewColors.textSecondary(colorScheme))
                        .multilineTextAlignment(.center)
                        .padding()
                    Spacer()
                } else {
                    List(viewModel.comments) { comment in
                        HStack(alignment: .top, spacing: 10) {
                            CreatorAvatarView(displayName: comment.authorName, avatarUrl: comment.authorAvatarUrl, size: 34)
                            VStack(alignment: .leading, spacing: 4) {
                                HStack(spacing: 4) {
                                    Text("@\(comment.authorUsername)")
                                        .font(.caption.bold())
                                        .foregroundStyle(SocialWiewColors.textPrimary(colorScheme))
                                    if comment.authorVerified || viewModel.verifiedUserIds.contains(comment.userId) || SocialRepository.isVerifiedUsername(comment.authorUsername) {
                                        VerifiedBadgeView(size: 12)
                                    }
                                    Spacer()
                                    if comment.userId == viewModel.currentUser?.uid {
                                        Button(role: .destructive) {
                                            viewModel.deleteComment(videoId: video.id, commentId: comment.id)
                                        } label: {
                                            Image(systemName: "trash")
                                                .font(.caption2)
                                        }
                                    }
                                }

                                if !comment.text.isEmpty {
                                    Text(comment.text)
                                        .font(.subheadline)
                                        .foregroundStyle(SocialWiewColors.textPrimary(colorScheme))
                                }

                                let gifSrc = comment.gifPreviewUrl.isEmpty ? comment.gifUrl : comment.gifPreviewUrl
                                if let url = URL(string: gifSrc), !gifSrc.isEmpty {
                                    AsyncImage(url: url) { phase in
                                        switch phase {
                                        case .success(let img):
                                            img
                                                .resizable()
                                                .scaledToFill()
                                                .frame(maxWidth: 180, maxHeight: 140)
                                                .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
                                        default:
                                            ProgressView()
                                                .frame(width: 120, height: 90)
                                        }
                                    }
                                }
                            }
                        }
                        .listRowBackground(SocialWiewColors.surface(colorScheme))
                    }
                    .listStyle(.plain)
                }

                if showGifPicker {
                    gifPickerPanel
                }

                if let selectedGif {
                    HStack {
                        Text("Seçilen GIF: \(selectedGif.title)")
                            .font(.caption)
                            .lineLimit(1)
                        Spacer()
                        Button("Kaldır") {
                            self.selectedGif = nil
                        }
                        .font(.caption.bold())
                    }
                    .padding(.horizontal)
                    .padding(.vertical, 6)
                    .background(SocialWiewColors.surfaceVariant(colorScheme))
                }

                HStack(spacing: 10) {
                    Button {
                        showGifPicker.toggle()
                        if showGifPicker && viewModel.tenorGifs.isEmpty {
                            viewModel.loadTenorGifs(query: "alkış")
                        }
                    } label: {
                        Text("GIF")
                            .font(.caption.bold())
                            .padding(.horizontal, 12)
                            .padding(.vertical, 8)
                            .foregroundStyle(showGifPicker ? .white : SocialWiewColors.textPrimary(colorScheme))
                            .liquidGlassCapsule(
                                tint: showGifPicker ? SocialWiewColors.primary(colorScheme) : nil,
                                interactive: true
                            )
                    }

                    TextField("Yorum yaz...", text: $commentText)
                        .textFieldStyle(.roundedBorder)

                    Button {
                        viewModel.addComment(videoId: video.id, text: commentText, gif: selectedGif)
                        commentText = ""
                        selectedGif = nil
                        showGifPicker = false
                    } label: {
                        Image(systemName: "paperplane.fill")
                            .foregroundStyle(.white)
                            .padding(10)
                            .liquidGlass(
                                in: Circle(),
                                tint: SocialWiewColors.primary(colorScheme),
                                interactive: true
                            )
                    }
                    .disabled(commentText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && selectedGif == nil)
                }
                .padding()
                .liquidGlass(in: RoundedRectangle(cornerRadius: 20, style: .continuous))
                .padding(.horizontal, 10)
                .padding(.bottom, 6)
            }
            .navigationTitle("Yorumlar (\(video.commentsCount))")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Kapat") {
                        viewModel.closeComments()
                    }
                }
            }
        }
        .presentationBackground(.ultraThinMaterial)
    }

    private var gifPickerPanel: some View {
        VStack(spacing: 8) {
            HStack {
                TextField("Tenor'da GIF ara...", text: $gifQuery)
                    .textFieldStyle(.roundedBorder)
                    .onSubmit {
                        viewModel.loadTenorGifs(query: gifQuery)
                    }
                Button("Ara") {
                    viewModel.loadTenorGifs(query: gifQuery)
                }
                .buttonStyle(.bordered)
            }
            .padding(.horizontal)

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(moodChips, id: \.self) { mood in
                        Button(mood) {
                            gifQuery = mood
                            viewModel.loadTenorGifs(query: mood)
                        }
                        .font(.caption)
                        .padding(.horizontal, 10)
                        .padding(.vertical, 5)
                        .background(SocialWiewColors.surfaceVariant(colorScheme))
                        .clipShape(Capsule())
                    }
                }
                .padding(.horizontal)
            }

            if viewModel.tenorLoading {
                ProgressView()
                    .frame(height: 120)
            } else if let err = viewModel.tenorError {
                Text(err)
                    .font(.caption)
                    .foregroundStyle(SocialWiewColors.neonCoral)
                    .padding()
            } else {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(viewModel.tenorGifs) { gif in
                            Button {
                                selectedGif = gif
                                showGifPicker = false
                            } label: {
                                if let url = URL(string: gif.previewUrl) {
                                    AsyncImage(url: url) { phase in
                                        switch phase {
                                        case .success(let img):
                                            img
                                                .resizable()
                                                .scaledToFill()
                                        default:
                                            Color.gray.opacity(0.2)
                                        }
                                    }
                                    .frame(width: 100, height: 90)
                                    .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
                                }
                            }
                        }
                    }
                    .padding(.horizontal)
                }
                .frame(height: 100)
            }
        }
        .padding(.vertical, 8)
        .background(SocialWiewColors.surface(colorScheme))
    }
}
