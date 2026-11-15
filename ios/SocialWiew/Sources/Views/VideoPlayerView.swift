import AVKit
import SwiftUI

struct VideoPlayerView: View {
    let video: VideoPost
    let isActive: Bool
    let isMuted: Bool
    let onResolveCloudVideo: (String, Int) async throws -> URL

    @State private var player: AVPlayer?
    @State private var isLoading = false
    @State private var loadError: String?
    @State private var loopObserver: NSObjectProtocol?

    var body: some View {
        ZStack {
            Color(hex: 0x06070B)

            if let thumbImage = decodeBase64Thumbnail(video.thumbnailData) {
                Image(uiImage: thumbImage)
                    .resizable()
                    .scaledToFill()
                    .opacity(player == nil ? 0.85 : 0)
            }

            if let player {
                VideoPlayer(player: player)
                    .disabled(true)
                    .aspectRatio(contentMode: .fill)
            }

            if isLoading {
                ProgressView()
                    .tint(.white)
                    .scaleEffect(1.2)
            }

            if let loadError {
                VStack(spacing: 8) {
                    Image(systemName: "exclamationmark.triangle.fill")
                        .font(.title2)
                        .foregroundStyle(SocialWiewColors.warmAmber)
                    Text(loadError)
                        .font(.caption)
                        .foregroundStyle(.white.opacity(0.85))
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 24)
                }
            }
        }
        .clipped()
        .task(id: video.id) {
            await loadVideoIfNeeded()
        }
        .onChange(of: isActive) { _, active in
            if active {
                player?.play()
            } else {
                player?.pause()
            }
        }
        .onChange(of: isMuted) { _, muted in
            player?.isMuted = muted
        }
        .onDisappear {
            player?.pause()
            if let loopObserver {
                NotificationCenter.default.removeObserver(loopObserver)
            }
        }
    }

    @MainActor
    private func loadVideoIfNeeded() async {
        loadError = nil
        var targetURL: URL?

        if !video.videoUrl.isEmpty, let remote = URL(string: video.videoUrl) {
            targetURL = remote
        } else if video.chunkCount > 0 {
            isLoading = true
            do {
                targetURL = try await onResolveCloudVideo(video.id, video.chunkCount)
            } catch {
                loadError = error.localizedDescription
            }
            isLoading = false
        } else {
            loadError = "Bu video henüz buluta yüklenmedi."
        }

        guard let url = targetURL else { return }
        let avPlayer = AVPlayer(url: url)
        avPlayer.isMuted = isMuted
        player = avPlayer

        if let loopObserver {
            NotificationCenter.default.removeObserver(loopObserver)
        }
        loopObserver = NotificationCenter.default.addObserver(
            forName: .AVPlayerItemDidPlayToEndTime,
            object: avPlayer.currentItem,
            queue: .main
        ) { _ in
            avPlayer.seek(to: .zero)
            if isActive {
                avPlayer.play()
            }
        }

        if isActive {
            avPlayer.play()
        }
    }

    private func decodeBase64Thumbnail(_ raw: String) -> UIImage? {
        guard !raw.isEmpty else { return nil }
        let cleaned: String
        if let commaIndex = raw.firstIndex(of: ",") {
            cleaned = String(raw[raw.index(after: commaIndex)...])
        } else {
            cleaned = raw
        }
        guard let data = Data(base64Encoded: cleaned, options: .ignoreUnknownCharacters) else {
            return nil
        }
        return UIImage(data: data)
    }
}
