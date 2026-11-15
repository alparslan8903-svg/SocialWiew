import AVFoundation
import MobileCoreServices
import UIKit

final class LiteUploadViewController: UIViewController, UIImagePickerControllerDelegate, UINavigationControllerDelegate {
    private let pickButton = UIButton(type: .system)
    private let selectedInfoLabel = UILabel()
    private let urlField = UITextField()
    private let captionField = UITextField()
    private let tagsField = UITextField()
    private let publishButton = UIButton(type: .system)
    private let spinner = UIActivityIndicatorView(style: .white)

    private var localVideoData: Data?
    private var localFileName: String?
    private var thumbnailBase64: String?
    private var durationMs: Int64 = 0

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = LiteColors.bg
        title = "Yükle"

        setupUI()
    }

    private func setupUI() {
        pickButton.setTitle("Galeriden Video Seç (Maks. 15 MB)", for: .normal)
        pickButton.setTitleColor(.white, for: .normal)
        pickButton.backgroundColor = LiteColors.surfaceVariant
        pickButton.titleLabel?.font = .boldSystemFont(ofSize: 15)
        pickButton.layer.cornerRadius = 12
        pickButton.heightAnchor.constraint(equalToConstant: 46).isActive = true
        pickButton.addTarget(self, action: #selector(pickVideoTapped), for: .touchUpInside)

        selectedInfoLabel.text = "Seçilen video yok"
        selectedInfoLabel.textColor = LiteColors.textSecondary
        selectedInfoLabel.font = .systemFont(ofSize: 13)
        selectedInfoLabel.numberOfLines = 2

        configureField(urlField, placeholder: "Veya HTTPS video bağlantısı...")
        urlField.autocapitalizationType = .none
        urlField.keyboardType = .URL

        configureField(captionField, placeholder: "Video açıklaması...")
        configureField(tagsField, placeholder: "Etiketler (virgülle: müzik, vlog)")
        tagsField.autocapitalizationType = .none

        publishButton.setTitle("Videoyu Paylaş", for: .normal)
        publishButton.setTitleColor(.white, for: .normal)
        publishButton.backgroundColor = LiteColors.neonCoral
        publishButton.titleLabel?.font = .boldSystemFont(ofSize: 16)
        publishButton.layer.cornerRadius = 14
        publishButton.heightAnchor.constraint(equalToConstant: 50).isActive = true
        publishButton.addTarget(self, action: #selector(publishTapped), for: .touchUpInside)

        spinner.hidesWhenStopped = true

        let stack = UIStackView(arrangedSubviews: [
            pickButton,
            selectedInfoLabel,
            urlField,
            captionField,
            tagsField,
            publishButton,
            spinner
        ])
        stack.axis = .vertical
        stack.spacing = 14
        stack.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(stack)

        NSLayoutConstraint.activate([
            stack.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor, constant: 20),
            stack.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 18),
            stack.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -18)
        ])
    }

    private func configureField(_ tf: UITextField, placeholder: String) {
        tf.backgroundColor = LiteColors.surface
        tf.textColor = LiteColors.textPrimary
        tf.layer.cornerRadius = 10
        tf.layer.borderColor = LiteColors.outline.cgColor
        tf.layer.borderWidth = 1
        tf.leftView = UIView(frame: CGRect(x: 0, y: 0, width: 12, height: 44))
        tf.leftViewMode = .always
        tf.heightAnchor.constraint(equalToConstant: 44).isActive = true
        tf.attributedPlaceholder = NSAttributedString(string: placeholder, attributes: [.foregroundColor: LiteColors.textSecondary])
    }

    @objc private func pickVideoTapped() {
        let picker = UIImagePickerController()
        picker.sourceType = .photoLibrary
        picker.mediaTypes = [kUTTypeMovie as String]
        picker.videoQuality = .typeMedium
        picker.delegate = self
        present(picker, animated: true)
    }

    func imagePickerController(_ picker: UIImagePickerController, didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey: Any]) {
        picker.dismiss(animated: true)
        guard let url = info[.mediaURL] as? URL else { return }
        prepareVideo(at: url)
    }

    func imagePickerControllerDidCancel(_ picker: UIImagePickerController) {
        picker.dismiss(animated: true)
    }

    private func prepareVideo(at url: URL) {
        spinner.startAnimating()
        DispatchQueue.global(qos: .userInitiated).async {
            do {
                let data = try Data(contentsOf: url)
                guard data.count <= 15_500_000 else {
                    DispatchQueue.main.async {
                        self.spinner.stopAnimating()
                        LiteToast.show("Video 15 MB'dan büyük olamaz.", in: self.view)
                    }
                    return
                }

                let asset = AVURLAsset(url: url)
                let ms = Int64((asset.duration.seconds.isFinite ? asset.duration.seconds : 0) * 1000)

                let generator = AVAssetImageGenerator(asset: asset)
                generator.appliesPreferredTrackTransform = true
                generator.maximumSize = CGSize(width: 360, height: 640)
                let cgImage = try? generator.copyCGImage(at: .zero, actualTime: nil)
                let jpeg = cgImage.flatMap { UIImage(cgImage: $0).jpegData(compressionQuality: 0.5) }

                DispatchQueue.main.async {
                    self.spinner.stopAnimating()
                    self.localVideoData = data
                    self.localFileName = url.lastPathComponent
                    self.durationMs = max(0, ms)
                    self.thumbnailBase64 = jpeg.map { "data:image/jpeg;base64,\($0.base64EncodedString())" }
                    let mb = Double(data.count) / (1024 * 1024)
                    self.selectedInfoLabel.text = String(format: "Seçildi: %@ (%.2f MB)", url.lastPathComponent, mb)
                    self.selectedInfoLabel.textColor = LiteColors.electricCyan
                }
            } catch {
                DispatchQueue.main.async {
                    self.spinner.stopAnimating()
                    LiteToast.show(error.localizedDescription, in: self.view)
                }
            }
        }
    }

    @objc private func publishTapped() {
        guard let author = LiteAppState.shared.currentProfile else {
            LiteToast.show("Profil yükleniyor...", in: view)
            return
        }
        let tags = (tagsField.text ?? "")
            .split(separator: ",")
            .map { $0.trimmingCharacters(in: .whitespacesAndNewlines).replacingOccurrences(of: "#", with: "") }
            .filter { !$0.isEmpty }

        publishButton.isEnabled = false
        spinner.startAnimating()

        LiteSocialRepository.shared.publishVideo(
            author: author,
            caption: captionField.text ?? "",
            videoUrl: urlField.text ?? "",
            localFileData: localVideoData,
            localFileName: localFileName,
            thumbnailData: thumbnailBase64,
            durationMs: durationMs,
            tags: Array(tags.prefix(5))
        ) { [weak self] result in
            guard let self else { return }
            self.spinner.stopAnimating()
            self.publishButton.isEnabled = true
            switch result {
            case .success:
                self.localVideoData = nil
                self.localFileName = nil
                self.thumbnailBase64 = nil
                self.durationMs = 0
                self.urlField.text = ""
                self.captionField.text = ""
                self.tagsField.text = ""
                self.selectedInfoLabel.text = "Seçilen video yok"
                self.selectedInfoLabel.textColor = LiteColors.textSecondary
                LiteToast.show("Video paylaşıldı!", in: self.view)
                self.tabBarController?.selectedIndex = 0
            case .failure(let err):
                LiteToast.show(err.localizedDescription, in: self.view)
            }
        }
    }
}
