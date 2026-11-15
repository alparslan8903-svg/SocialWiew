import AVFoundation
import PhotosUI
import SwiftUI
import UniformTypeIdentifiers

struct PickedVideoTransferable: Transferable {
    let url: URL

    static var transferRepresentation: some TransferRepresentation {
        FileRepresentation(contentType: .movie) { movie in
            SentTransferredFile(movie.url)
        } importing: { received in
            let copy = FileManager.default.temporaryDirectory
                .appendingPathComponent("picked_\(UUID().uuidString).mp4")
            try? FileManager.default.removeItem(at: copy)
            try FileManager.default.copyItem(at: received.file, to: copy)
            return Self(url: copy)
        }
    }
}

struct UploadView: View {
    @Environment(\.colorScheme) private var colorScheme
    @ObservedObject var viewModel: SocialFeedViewModel
    let onPublished: () -> Void

    @State private var selectedItem: PhotosPickerItem?
    @State private var localVideoData: Data?
    @State private var localFileName: String?
    @State private var thumbnailBase64: String?
    @State private var durationMs: Int64 = 0
    @State private var videoUrl = ""
    @State private var caption = ""
    @State private var tagsText = ""
    @State private var preparationError: String?
    @State private var isPreparingVideo = false

    var body: some View {
        NavigationStack {
            Form {
                Section("Video Kaynağı") {
                    PhotosPicker(
                        selection: $selectedItem,
                        matching: .videos,
                        photoLibrary: .shared()
                    ) {
                        Label(
                            localFileName ?? "Galeriden Video Seç (Maks. 15 MB)",
                            systemImage: "photo.on.rectangle.angled"
                        )
                    }

                    if isPreparingVideo {
                        HStack(spacing: 10) {
                            ProgressView()
                            Text("Video hazırlanıyor...")
                                .font(.caption)
                                .foregroundStyle(SocialWiewColors.textSecondary(colorScheme))
                        }
                    }

                    if let localFileName, let localVideoData {
                        let mb = Double(localVideoData.count) / (1024 * 1024)
                        Text(String(format: "Seçildi: %@ (%.2f MB)", localFileName, mb))
                            .font(.caption)
                            .foregroundStyle(SocialWiewColors.secondary(colorScheme))
                    }

                    TextField("Veya doğrudan HTTPS video bağlantısı girin...", text: $videoUrl)
                        .keyboardType(.URL)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                }

                Section("Açıklama ve Etiketler") {
                    TextField("Videon hakkında bir şeyler yaz...", text: $caption, axis: .vertical)
                        .lineLimit(3...6)

                    TextField("Etiketler (virgülle ayır: dans, müzik, vlog)", text: $tagsText)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                }

                if let preparationError {
                    Section {
                        Text(preparationError)
                            .font(.caption)
                            .foregroundStyle(SocialWiewColors.neonCoral)
                    }
                }

                Section {
                    Button {
                        let tags = tagsText
                            .split(separator: ",")
                            .map { $0.trimmingCharacters(in: .whitespacesAndNewlines).replacingOccurrences(of: "#", with: "") }
                            .filter { !$0.isEmpty }
                        viewModel.publishVideo(
                            caption: caption,
                            videoUrl: videoUrl,
                            localFileData: localVideoData,
                            localFileName: localFileName,
                            thumbnailData: thumbnailBase64,
                            durationMs: durationMs,
                            tags: Array(tags.prefix(5))
                        ) {
                            selectedItem = nil
                            localVideoData = nil
                            localFileName = nil
                            thumbnailBase64 = nil
                            durationMs = 0
                            videoUrl = ""
                            caption = ""
                            tagsText = ""
                            onPublished()
                        }
                    } label: {
                        HStack {
                            Spacer()
                            if viewModel.isPublishing {
                                ProgressView()
                                    .tint(.white)
                            } else {
                                Text("Videoyu Paylaş")
                                    .font(.headline)
                            }
                            Spacer()
                        }
                        .padding(.vertical, 6)
                    }
                    .listRowBackground(SocialWiewColors.primary(colorScheme))
                    .foregroundStyle(.white)
                    .disabled(viewModel.isPublishing || isPreparingVideo || (localVideoData == nil && videoUrl.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty))
                }
            }
            .navigationTitle("Yeni Video Paylaş")
            .onChange(of: selectedItem) { _, item in
                guard let item else { return }
                Task { await prepareSelectedVideo(item) }
            }
        }
    }

    @MainActor
    private func prepareSelectedVideo(_ item: PhotosPickerItem) async {
        isPreparingVideo = true
        preparationError = nil
        defer { isPreparingVideo = false }

        do {
            guard let picked = try await item.loadTransferable(type: PickedVideoTransferable.self) else {
                preparationError = "Video dosyası okunamadı."
                return
            }
            let data = try Data(contentsOf: picked.url)
            guard data.count <= 15_500_000 else {
                preparationError = "Video boyutu en fazla 15 MB olabilir. Lütfen daha kısa bir video seçin."
                return
            }

            let asset = AVURLAsset(url: picked.url)
            let cmDuration = try await asset.load(.duration)
            let ms = Int64((cmDuration.seconds.isFinite ? cmDuration.seconds : 0) * 1000)

            let generator = AVAssetImageGenerator(asset: asset)
            generator.appliesPreferredTrackTransform = true
            generator.maximumSize = CGSize(width: 480, height: 854)
            let cgImage = try generator.copyCGImage(at: .zero, actualTime: nil)
            let uiImage = UIImage(cgImage: cgImage)
            let jpeg = uiImage.jpegData(compressionQuality: 0.6)

            localVideoData = data
            localFileName = picked.url.lastPathComponent
            durationMs = max(0, ms)
            thumbnailBase64 = jpeg.map { "data:image/jpeg;base64,\($0.base64EncodedString())" }
        } catch {
            preparationError = error.localizedDescription
        }
    }
}
