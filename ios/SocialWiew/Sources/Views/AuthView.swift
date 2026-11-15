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

            VStack(spacing: 24) {
                Spacer()

                ZStack {
                    RoundedRectangle(cornerRadius: 28, style: .continuous)
                        .fill(
                            LinearGradient(
                                colors: [
                                    SocialWiewColors.neonCoral.opacity(0.25),
                                    SocialWiewColors.electricCyan.opacity(0.22)
                                ],
                                startPoint: .topLeading,
                                endPoint: .bottomTrailing
                            )
                        )
                        .frame(width: 96, height: 96)

                    Image(systemName: "play.rectangle.fill")
                        .font(.system(size: 42, weight: .bold))
                        .foregroundStyle(SocialWiewColors.primary(colorScheme))
                }

                VStack(spacing: 10) {
                    Text("SocialWiew")
                        .font(.system(size: 34, weight: .bold, design: .rounded))
                        .foregroundStyle(SocialWiewColors.textPrimary(colorScheme))

                    Text("Kısa videolar paylaş, takip et ve GIF'lerle tepki ver")
                        .font(.body)
                        .multilineTextAlignment(.center)
                        .foregroundStyle(SocialWiewColors.textSecondary(colorScheme))
                        .padding(.horizontal, 24)
                }

                if let errorMessage {
                    Text(errorMessage)
                        .font(.footnote)
                        .foregroundStyle(SocialWiewColors.neonCoral)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 24)
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
                    .background(SocialWiewColors.primary(colorScheme))
                    .foregroundStyle(.white)
                    .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
                }
                .disabled(isSigningIn)
                .padding(.horizontal, 28)

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
