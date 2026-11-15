import SwiftUI
import FirebaseAuth
import FirebaseCore
import GoogleSignIn
import GoogleSignInSwift

struct AuthView: View {
    @Environment(\.colorScheme) private var colorScheme
    @State private var isSigningIn = false
    @State private var errorMessage: String?

    var body: some View {
        ZStack {
            SocialWiewColors.bg(colorScheme)
                .ignoresSafeArea()

            Circle()
                .fill(SocialWiewColors.neonCoral.opacity(0.28))
                .frame(width: 280, height: 280)
                .blur(radius: 72)
                .offset(x: -110, y: -220)

            Circle()
                .fill(SocialWiewColors.electricCyan.opacity(0.24))
                .frame(width: 260, height: 260)
                .blur(radius: 72)
                .offset(x: 120, y: 180)

            VStack(spacing: 24) {
                Spacer()

                VStack(spacing: 20) {
                    ZStack {
                        RoundedRectangle(cornerRadius: 24, style: .continuous)
                            .fill(
                                LinearGradient(
                                    colors: [
                                        SocialWiewColors.neonCoral.opacity(0.32),
                                        SocialWiewColors.electricCyan.opacity(0.26)
                                    ],
                                    startPoint: .topLeading,
                                    endPoint: .bottomTrailing
                                )
                            )
                            .frame(width: 88, height: 88)
                            .liquidGlass(in: RoundedRectangle(cornerRadius: 24, style: .continuous))

                        Image(systemName: "play.rectangle.fill")
                            .font(.system(size: 38, weight: .bold))
                            .foregroundStyle(SocialWiewColors.primary(colorScheme))
                    }

                    VStack(spacing: 8) {
                        Text("SocialWiew")
                            .font(.system(size: 34, weight: .bold, design: .rounded))
                            .foregroundStyle(SocialWiewColors.textPrimary(colorScheme))

                        Text("Kısa videolar paylaş, takip et ve GIF'lerle tepki ver")
                            .font(.body)
                            .multilineTextAlignment(.center)
                            .foregroundStyle(SocialWiewColors.textSecondary(colorScheme))
                    }

                    if let errorMessage {
                        Text(errorMessage)
                            .font(.footnote)
                            .foregroundStyle(SocialWiewColors.neonCoral)
                            .multilineTextAlignment(.center)
                    }

                    Button(action: signInWithGoogle) {
                        HStack(spacing: 12) {
                            if isSigningIn {
                                ProgressView()
                                    .tint(.white)
                            } else {
                                Image(systemName: "person.crop.circle.badge.checkmark")
                                    .font(.headline)
                            }
                            Text(isSigningIn ? "Giriş yapılıyor..." : "Google ile Giriş Yap")
                                .font(.headline)
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 16)
                        .foregroundStyle(.white)
                        .liquidGlassCapsule(
                            tint: SocialWiewColors.primary(colorScheme),
                            interactive: true
                        )
                    }
                    .disabled(isSigningIn)
                }
                .padding(24)
                .liquidGlass(in: RoundedRectangle(cornerRadius: 28, style: .continuous))
                .padding(.horizontal, 22)

                Spacer()

                Text("App Store ve Firebase Auth ile güvenli oturum")
                    .font(.caption)
                    .foregroundStyle(SocialWiewColors.textSecondary(colorScheme))
                    .padding(.bottom, 16)
            }
        }
    }

    private func signInWithGoogle() {
        guard let clientID = FirebaseApp.app()?.options.clientID else {
            errorMessage = "GoogleService-Info.plist dosyasında CLIENT_ID bulunamadı."
            return
        }
        guard let scene = UIApplication.shared.connectedScenes.first as? UIWindowScene,
              let rootVC = scene.windows.first?.rootViewController else {
            errorMessage = "Aktif pencere bulunamadı."
            return
        }

        isSigningIn = true
        errorMessage = nil

        GIDSignIn.sharedInstance.configuration = GIDConfiguration(clientID: clientID)
        Task {
            do {
                let result = try await GIDSignIn.sharedInstance.signIn(withPresenting: rootVC)
                guard let idToken = result.user.idToken?.tokenString else {
                    throw NSError(domain: "AuthView", code: -1, userInfo: [
                        NSLocalizedDescriptionKey: "Google kimlik belirteci alınamadı."
                    ])
                }
                let credential = GoogleAuthProvider.credential(
                    withIDToken: idToken,
                    accessToken: result.user.accessToken.tokenString
                )
                try await Auth.auth().signIn(with: credential)
            } catch {
                errorMessage = error.localizedDescription
            }
            isSigningIn = false
        }
    }
}
