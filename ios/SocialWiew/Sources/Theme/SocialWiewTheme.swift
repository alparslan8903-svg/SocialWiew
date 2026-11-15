import SwiftUI

enum AppThemeMode: String, CaseIterable, Identifiable {
    case dark
    case light
    case system

    var id: String { rawValue }

    var label: String {
        switch self {
        case .dark: return "Koyu"
        case .light: return "Açık"
        case .system: return "Sistem"
        }
    }

    var colorScheme: ColorScheme? {
        switch self {
        case .dark: return .dark
        case .light: return .light
        case .system: return nil
        }
    }
}

enum SocialWiewColors {
    // Obsidian Neon Studio — Dark Palette
    static let obsidianBg = Color(hex: 0x090A0F)
    static let obsidianSurface = Color(hex: 0x11131C)
    static let obsidianSurfaceVariant = Color(hex: 0x1A1E2B)
    static let obsidianSurfaceHigh = Color(hex: 0x23283A)
    static let obsidianOutline = Color(hex: 0x2F364D)
    static let obsidianTextPrimary = Color(hex: 0xF4F6FB)
    static let obsidianTextSecondary = Color(hex: 0xA0A8C0)

    static let neonCoral = Color(hex: 0xFF3B6B)
    static let neonCoralDarkContainer = Color(hex: 0x540D22)
    static let electricCyan = Color(hex: 0x00E5C3)
    static let electricCyanDarkContainer = Color(hex: 0x004F42)
    static let warmAmber = Color(hex: 0xFFB84D)

    // Crisp Alabaster — Light Palette
    static let lightBg = Color(hex: 0xF6F7FB)
    static let lightSurface = Color(hex: 0xFFFFFF)
    static let lightSurfaceVariant = Color(hex: 0xECEFF6)
    static let lightOutline = Color(hex: 0xD2D8E6)
    static let lightTextPrimary = Color(hex: 0x11131C)
    static let lightTextSecondary = Color(hex: 0x5A627A)
    static let lightCoral = Color(hex: 0xE11D52)
    static let lightTeal = Color(hex: 0x00A88F)

    static func bg(_ scheme: ColorScheme) -> Color {
        scheme == .dark ? obsidianBg : lightBg
    }

    static func surface(_ scheme: ColorScheme) -> Color {
        scheme == .dark ? obsidianSurface : lightSurface
    }

    static func surfaceVariant(_ scheme: ColorScheme) -> Color {
        scheme == .dark ? obsidianSurfaceVariant : lightSurfaceVariant
    }

    static func outline(_ scheme: ColorScheme) -> Color {
        scheme == .dark ? obsidianOutline : lightOutline
    }

    static func textPrimary(_ scheme: ColorScheme) -> Color {
        scheme == .dark ? obsidianTextPrimary : lightTextPrimary
    }

    static func textSecondary(_ scheme: ColorScheme) -> Color {
        scheme == .dark ? obsidianTextSecondary : lightTextSecondary
    }

    static func primary(_ scheme: ColorScheme) -> Color {
        scheme == .dark ? neonCoral : lightCoral
    }

    static func secondary(_ scheme: ColorScheme) -> Color {
        scheme == .dark ? electricCyan : lightTeal
    }
}

extension Color {
    init(hex: UInt32, alpha: Double = 1.0) {
        let r = Double((hex >> 16) & 0xFF) / 255.0
        let g = Double((hex >> 8) & 0xFF) / 255.0
        let b = Double(hex & 0xFF) / 255.0
        self.init(.sRGB, red: r, green: g, blue: b, opacity: alpha)
    }
}

struct VerifiedBadgeView: View {
    var size: CGFloat = 15

    var body: some View {
        Image(systemName: "checkmark.seal.fill")
            .font(.system(size: size, weight: .semibold))
            .foregroundStyle(Color(hex: 0x38BDF8))
            .accessibilityLabel("Onaylı Hesap")
    }
}

struct CreatorAvatarView: View {
    let displayName: String
    let avatarUrl: String
    var size: CGFloat = 44

    private var initials: String {
        let parts = displayName
            .split(separator: " ")
            .prefix(2)
            .compactMap { $0.first }
        let joined = String(parts).uppercased()
        return joined.isEmpty ? "S" : joined
    }

    var body: some View {
        Group {
            if let url = URL(string: avatarUrl), !avatarUrl.isEmpty {
                AsyncImage(url: url) { phase in
                    switch phase {
                    case .success(let image):
                        image
                            .resizable()
                            .scaledToFill()
                    default:
                        fallbackCircle
                    }
                }
            } else {
                fallbackCircle
            }
        }
        .frame(width: size, height: size)
        .clipShape(Circle())
    }

    private var fallbackCircle: some View {
        ZStack {
            LinearGradient(
                colors: [SocialWiewColors.neonCoral, SocialWiewColors.electricCyan],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
            Text(initials)
                .font(.system(size: size * 0.38, weight: .bold))
                .foregroundStyle(.white)
        }
    }
}

struct LiquidGlassGroup<Content: View>: View {
    var spacing: CGFloat = 12
    @ViewBuilder let content: () -> Content

    var body: some View {
        #if compiler(>=6.2)
        if #available(iOS 26.0, *) {
            GlassEffectContainer(spacing: spacing) {
                content()
            }
        } else {
            content()
        }
        #else
        content()
        #endif
    }
}

struct LiquidGlassModifier<S: InsettableShape>: ViewModifier {
    let shape: S
    let tint: Color?
    let interactive: Bool

    @Environment(\.colorScheme) private var colorScheme

    func body(content: Content) -> some View {
        #if compiler(>=6.2)
        if #available(iOS 26.0, *) {
            content.glassEffect(makeGlass(), in: shape)
        } else {
            fallbackGlass(content)
        }
        #else
        fallbackGlass(content)
        #endif
    }

    #if compiler(>=6.2)
    @available(iOS 26.0, *)
    private func makeGlass() -> Glass {
        var glass = Glass.regular
        if let tint {
            glass = glass.tint(tint)
        }
        if interactive {
            glass = glass.interactive()
        }
        return glass
    }
    #endif

    @ViewBuilder
    private func fallbackGlass(_ content: Content) -> some View {
        content
            .background {
                ZStack {
                    shape.fill(.ultraThinMaterial)
                    if let tint {
                        shape.fill(tint.opacity(colorScheme == .dark ? 0.22 : 0.16))
                    }
                }
            }
            .overlay {
                shape.strokeBorder(
                    LinearGradient(
                        colors: [
                            Color.white.opacity(colorScheme == .dark ? 0.32 : 0.65),
                            Color.white.opacity(0.06)
                        ],
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    ),
                    lineWidth: 0.9
                )
            }
            .clipShape(shape)
            .shadow(color: .black.opacity(colorScheme == .dark ? 0.32 : 0.10), radius: 12, x: 0, y: 6)
    }
}

extension View {
    func liquidGlass<S: InsettableShape>(
        in shape: S,
        tint: Color? = nil,
        interactive: Bool = false
    ) -> some View {
        modifier(LiquidGlassModifier(shape: shape, tint: tint, interactive: interactive))
    }

    func liquidGlassCapsule(tint: Color? = nil, interactive: Bool = false) -> some View {
        liquidGlass(in: Capsule(), tint: tint, interactive: interactive)
    }
}

