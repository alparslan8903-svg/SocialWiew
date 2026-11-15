import UIKit

enum LiteColors {
    static let bg = UIColor(hex: 0x090A0F)
    static let surface = UIColor(hex: 0x11131C)
    static let surfaceVariant = UIColor(hex: 0x1A1E2B)
    static let outline = UIColor(hex: 0x2F364D)
    static let textPrimary = UIColor(hex: 0xF4F6FB)
    static let textSecondary = UIColor(hex: 0xA0A8C0)
    static let neonCoral = UIColor(hex: 0xFF3B6B)
    static let electricCyan = UIColor(hex: 0x00E5C3)
    static let verifiedBlue = UIColor(hex: 0x38BDF8)
}

extension UIColor {
    convenience init(hex: UInt32, alpha: CGFloat = 1.0) {
        let r = CGFloat((hex >> 16) & 0xFF) / 255.0
        let g = CGFloat((hex >> 8) & 0xFF) / 255.0
        let b = CGFloat(hex & 0xFF) / 255.0
        self.init(red: r, green: g, blue: b, alpha: alpha)
    }
}

enum LiteIcons {
    enum Kind {
        case feed
        case discover
        case search
        case upload
        case profile
        case heart(filled: Bool)
        case comment
        case share
        case volume(muted: Bool)
        case close
        case trash
        case verified
    }

    static func icon(_ kind: Kind, size: CGFloat = 22, color: UIColor = .white) -> UIImage {
        let renderer = UIGraphicsImageRenderer(size: CGSize(width: size, height: size))
        return renderer.image { ctx in
            let cg = ctx.cgContext
            cg.setStrokeColor(color.cgColor)
            cg.setFillColor(color.cgColor)
            cg.setLineWidth(max(1.6, size * 0.085))
            cg.setLineCap(.round)
            cg.setLineJoin(.round)

            let w = size
            let h = size

            switch kind {
            case .feed:
                let path = UIBezierPath()
                path.move(to: CGPoint(x: w * 0.14, y: h * 0.48))
                path.addLine(to: CGPoint(x: w * 0.50, y: h * 0.14))
                path.addLine(to: CGPoint(x: w * 0.86, y: h * 0.48))
                path.move(to: CGPoint(x: w * 0.24, y: h * 0.44))
                path.addLine(to: CGPoint(x: w * 0.24, y: h * 0.86))
                path.addLine(to: CGPoint(x: w * 0.76, y: h * 0.86))
                path.addLine(to: CGPoint(x: w * 0.76, y: h * 0.44))
                path.lineWidth = max(1.6, size * 0.085)
                path.stroke()

            case .discover:
                let circle = UIBezierPath(ovalIn: CGRect(x: w * 0.14, y: h * 0.14, width: w * 0.72, height: h * 0.72))
                circle.lineWidth = max(1.6, size * 0.085)
                circle.stroke()
                let diamond = UIBezierPath()
                diamond.move(to: CGPoint(x: w * 0.62, y: h * 0.38))
                diamond.addLine(to: CGPoint(x: w * 0.54, y: h * 0.54))
                diamond.addLine(to: CGPoint(x: w * 0.38, y: h * 0.62))
                diamond.addLine(to: CGPoint(x: w * 0.46, y: h * 0.46))
                diamond.close()
                diamond.fill()

            case .search:
                let circle = UIBezierPath(ovalIn: CGRect(x: w * 0.16, y: h * 0.16, width: w * 0.50, height: h * 0.50))
                circle.lineWidth = max(1.6, size * 0.085)
                circle.stroke()
                let handle = UIBezierPath()
                handle.move(to: CGPoint(x: w * 0.59, y: h * 0.59))
                handle.addLine(to: CGPoint(x: w * 0.84, y: h * 0.84))
                handle.lineWidth = max(1.8, size * 0.095)
                handle.stroke()

            case .upload:
                let circle = UIBezierPath(ovalIn: CGRect(x: w * 0.14, y: h * 0.14, width: w * 0.72, height: h * 0.72))
                circle.lineWidth = max(1.6, size * 0.085)
                circle.stroke()
                let plus = UIBezierPath()
                plus.move(to: CGPoint(x: w * 0.50, y: h * 0.32))
                plus.addLine(to: CGPoint(x: w * 0.50, y: h * 0.68))
                plus.move(to: CGPoint(x: w * 0.32, y: h * 0.50))
                plus.addLine(to: CGPoint(x: w * 0.68, y: h * 0.50))
                plus.lineWidth = max(1.8, size * 0.09)
                plus.stroke()

            case .profile:
                let head = UIBezierPath(ovalIn: CGRect(x: w * 0.34, y: h * 0.16, width: w * 0.32, height: h * 0.32))
                head.lineWidth = max(1.6, size * 0.085)
                head.stroke()
                let body = UIBezierPath()
                body.move(to: CGPoint(x: w * 0.18, y: h * 0.84))
                body.addQuadCurve(to: CGPoint(x: w * 0.82, y: h * 0.84), controlPoint: CGPoint(x: w * 0.50, y: h * 0.54))
                body.lineWidth = max(1.6, size * 0.085)
                body.stroke()

            case .heart(let filled):
                let heart = UIBezierPath()
                heart.move(to: CGPoint(x: w * 0.50, y: h * 0.84))
                heart.addCurve(
                    to: CGPoint(x: w * 0.12, y: h * 0.38),
                    controlPoint1: CGPoint(x: w * 0.24, y: h * 0.68),
                    controlPoint2: CGPoint(x: w * 0.12, y: h * 0.54)
                )
                heart.addArc(
                    withCenter: CGPoint(x: w * 0.31, y: h * 0.35),
                    radius: w * 0.19,
                    startAngle: .pi,
                    endAngle: 0,
                    clockwise: true
                )
                heart.addArc(
                    withCenter: CGPoint(x: w * 0.69, y: h * 0.35),
                    radius: w * 0.19,
                    startAngle: .pi,
                    endAngle: 0,
                    clockwise: true
                )
                heart.addCurve(
                    to: CGPoint(x: w * 0.50, y: h * 0.84),
                    controlPoint1: CGPoint(x: w * 0.88, y: h * 0.54),
                    controlPoint2: CGPoint(x: w * 0.76, y: h * 0.68)
                )
                heart.close()
                heart.lineWidth = max(1.6, size * 0.085)
                if filled {
                    heart.fill()
                } else {
                    heart.stroke()
                }

            case .comment:
                let rect = CGRect(x: w * 0.14, y: h * 0.18, width: w * 0.72, height: h * 0.52)
                let bubble = UIBezierPath(roundedRect: rect, cornerRadius: w * 0.16)
                bubble.fill()
                let tail = UIBezierPath()
                tail.move(to: CGPoint(x: w * 0.30, y: h * 0.68))
                tail.addLine(to: CGPoint(x: w * 0.24, y: h * 0.84))
                tail.addLine(to: CGPoint(x: w * 0.46, y: h * 0.68))
                tail.close()
                tail.fill()

            case .share:
                let arrow = UIBezierPath()
                arrow.move(to: CGPoint(x: w * 0.18, y: h * 0.78))
                arrow.addQuadCurve(to: CGPoint(x: w * 0.58, y: h * 0.40), controlPoint: CGPoint(x: w * 0.22, y: h * 0.42))
                arrow.addLine(to: CGPoint(x: w * 0.58, y: h * 0.24))
                arrow.addLine(to: CGPoint(x: w * 0.86, y: h * 0.48))
                arrow.addLine(to: CGPoint(x: w * 0.58, y: h * 0.72))
                arrow.addLine(to: CGPoint(x: w * 0.58, y: h * 0.56))
                arrow.addQuadCurve(to: CGPoint(x: w * 0.18, y: h * 0.78), controlPoint: CGPoint(x: w * 0.34, y: h * 0.54))
                arrow.close()
                arrow.fill()

            case .volume(let muted):
                let speaker = UIBezierPath()
                speaker.move(to: CGPoint(x: w * 0.18, y: h * 0.40))
                speaker.addLine(to: CGPoint(x: w * 0.34, y: h * 0.40))
                speaker.addLine(to: CGPoint(x: w * 0.54, y: h * 0.22))
                speaker.addLine(to: CGPoint(x: w * 0.54, y: h * 0.78))
                speaker.addLine(to: CGPoint(x: w * 0.34, y: h * 0.60))
                speaker.addLine(to: CGPoint(x: w * 0.18, y: h * 0.60))
                speaker.close()
                speaker.fill()

                let wave = UIBezierPath()
                if muted {
                    wave.move(to: CGPoint(x: w * 0.66, y: h * 0.36))
                    wave.addLine(to: CGPoint(x: w * 0.86, y: h * 0.64))
                    wave.move(to: CGPoint(x: w * 0.86, y: h * 0.36))
                    wave.addLine(to: CGPoint(x: w * 0.66, y: h * 0.64))
                } else {
                    wave.addArc(withCenter: CGPoint(x: w * 0.54, y: h * 0.50), radius: w * 0.20, startAngle: -.pi / 4, endAngle: .pi / 4, clockwise: true)
                }
                wave.lineWidth = max(1.6, size * 0.085)
                wave.stroke()

            case .close:
                let xPath = UIBezierPath()
                xPath.move(to: CGPoint(x: w * 0.24, y: h * 0.24))
                xPath.addLine(to: CGPoint(x: w * 0.76, y: h * 0.76))
                xPath.move(to: CGPoint(x: w * 0.76, y: h * 0.24))
                xPath.addLine(to: CGPoint(x: w * 0.24, y: h * 0.76))
                xPath.lineWidth = max(1.8, size * 0.095)
                xPath.stroke()

            case .trash:
                let lid = UIBezierPath()
                lid.move(to: CGPoint(x: w * 0.22, y: h * 0.28))
                lid.addLine(to: CGPoint(x: w * 0.78, y: h * 0.28))
                lid.move(to: CGPoint(x: w * 0.38, y: h * 0.28))
                lid.addLine(to: CGPoint(x: w * 0.38, y: h * 0.18))
                lid.addLine(to: CGPoint(x: w * 0.62, y: h * 0.18))
                lid.addLine(to: CGPoint(x: w * 0.62, y: h * 0.28))
                lid.lineWidth = max(1.5, size * 0.08)
                lid.stroke()
                let bin = UIBezierPath(roundedRect: CGRect(x: w * 0.28, y: h * 0.32, width: w * 0.44, height: h * 0.52), cornerRadius: 2)
                bin.lineWidth = max(1.5, size * 0.08)
                bin.stroke()

            case .verified:
                let badge = UIBezierPath(ovalIn: CGRect(x: w * 0.10, y: h * 0.10, width: w * 0.80, height: h * 0.80))
                badge.fill()
                cg.setStrokeColor(UIColor.white.cgColor)
                let check = UIBezierPath()
                check.move(to: CGPoint(x: w * 0.30, y: h * 0.52))
                check.addLine(to: CGPoint(x: w * 0.45, y: h * 0.67))
                check.addLine(to: CGPoint(x: w * 0.72, y: h * 0.35))
                check.lineWidth = max(1.8, size * 0.11)
                check.stroke()
            }
        }
    }
}

final class LiteImageLoader {
    static let shared = LiteImageLoader()
    private let cache = NSCache<NSString, UIImage>()

    private init() {
        cache.countLimit = 40
        cache.totalCostLimit = 12 * 1024 * 1024
    }

    func imageFromBase64(_ raw: String, key: String) -> UIImage? {
        if let cached = cache.object(forKey: key as NSString) {
            return cached
        }
        guard !raw.isEmpty else { return nil }
        let cleaned = raw.components(separatedBy: ",").last ?? raw
        guard let data = Data(base64Encoded: cleaned, options: .ignoreUnknownCharacters),
              let image = UIImage(data: data) else {
            return nil
        }
        cache.setObject(image, forKey: key as NSString, cost: data.count)
        return image
    }

    @discardableResult
    func loadURL(_ urlString: String, completion: @escaping (UIImage?) -> Void) -> URLSessionDataTask? {
        guard !urlString.isEmpty, let url = URL(string: urlString) else {
            completion(nil)
            return nil
        }
        if let cached = cache.object(forKey: urlString as NSString) {
            completion(cached)
            return nil
        }
        let task = URLSession.shared.dataTask(with: url) { [weak self] data, _, _ in
            guard let data, let image = UIImage(data: data) else {
                DispatchQueue.main.async { completion(nil) }
                return
            }
            self?.cache.setObject(image, forKey: urlString as NSString, cost: data.count)
            DispatchQueue.main.async { completion(image) }
        }
        task.resume()
        return task
    }
}

final class LiteAvatarView: UIView {
    private let imageView = UIImageView()
    private let initialsLabel = UILabel()
    private var currentTask: URLSessionDataTask?

    override init(frame: CGRect) {
        super.init(frame: frame)
        setup()
    }

    required init?(coder: NSCoder) {
        super.init(coder: coder)
        setup()
    }

    private func setup() {
        backgroundColor = LiteColors.neonCoral
        clipsToBounds = true

        imageView.contentMode = .scaleAspectFill
        imageView.translatesAutoresizingMaskIntoConstraints = false
        addSubview(imageView)

        initialsLabel.textColor = .white
        initialsLabel.font = .boldSystemFont(ofSize: 15)
        initialsLabel.textAlignment = .center
        initialsLabel.translatesAutoresizingMaskIntoConstraints = false
        addSubview(initialsLabel)

        NSLayoutConstraint.activate([
            imageView.topAnchor.constraint(equalTo: topAnchor),
            imageView.leadingAnchor.constraint(equalTo: leadingAnchor),
            imageView.trailingAnchor.constraint(equalTo: trailingAnchor),
            imageView.bottomAnchor.constraint(equalTo: bottomAnchor),
            initialsLabel.centerXAnchor.constraint(equalTo: centerXAnchor),
            initialsLabel.centerYAnchor.constraint(equalTo: centerYAnchor)
        ])
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        layer.cornerRadius = bounds.width / 2
    }

    func configure(displayName: String, avatarUrl: String, size: CGFloat) {
        currentTask?.cancel()
        currentTask = nil
        imageView.image = nil

        let parts = displayName.split(separator: " ").prefix(2).compactMap { $0.first }
        let initials = String(parts).uppercased()
        initialsLabel.text = initials.isEmpty ? "S" : initials
        initialsLabel.font = .boldSystemFont(ofSize: max(12, size * 0.38))
        initialsLabel.isHidden = false

        if !avatarUrl.isEmpty {
            currentTask = LiteImageLoader.shared.loadURL(avatarUrl) { [weak self] img in
                guard let self, let img else { return }
                self.imageView.image = img
                self.initialsLabel.isHidden = true
            }
        }
    }
}

enum LiteToast {
    static func show(_ message: String, in view: UIView) {
        let label = UILabel()
        label.text = message
        label.textColor = LiteColors.textPrimary
        label.backgroundColor = LiteColors.surface
        label.font = .systemFont(ofSize: 13, weight: .semibold)
        label.textAlignment = .center
        label.numberOfLines = 2
        label.layer.cornerRadius = 14
        label.layer.borderColor = LiteColors.outline.cgColor
        label.layer.borderWidth = 1
        label.clipsToBounds = true
        label.translatesAutoresizingMaskIntoConstraints = false

        let container = UIView()
        container.backgroundColor = LiteColors.surface
        container.layer.cornerRadius = 14
        container.layer.borderColor = LiteColors.outline.cgColor
        container.layer.borderWidth = 1
        container.translatesAutoresizingMaskIntoConstraints = false
        label.layer.borderWidth = 0
        container.addSubview(label)
        view.addSubview(container)

        NSLayoutConstraint.activate([
            label.topAnchor.constraint(equalTo: container.topAnchor, constant: 10),
            label.bottomAnchor.constraint(equalTo: container.bottomAnchor, constant: -10),
            label.leadingAnchor.constraint(equalTo: container.leadingAnchor, constant: 14),
            label.trailingAnchor.constraint(equalTo: container.trailingAnchor, constant: -14),
            container.centerXAnchor.constraint(equalTo: view.centerXAnchor),
            container.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor, constant: 12),
            container.widthAnchor.constraint(lessThanOrEqualTo: view.widthAnchor, constant: -32)
        ])

        container.alpha = 0
        UIView.animate(withDuration: 0.2) {
            container.alpha = 1
        }
        DispatchQueue.main.asyncAfter(deadline: .now() + 2.6) {
            UIView.animate(withDuration: 0.25, animations: {
                container.alpha = 0
            }, completion: { _ in
                container.removeFromSuperview()
            })
        }
    }
}
