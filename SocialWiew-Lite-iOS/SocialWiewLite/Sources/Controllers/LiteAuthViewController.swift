import UIKit
import FirebaseAuth
import FirebaseCore
import GoogleSignIn

final class LiteAuthViewController: UIViewController {
    private let signInButton = UIButton(type: .system)
    private let spinner = UIActivityIndicatorView(style: .white)
    private let errorLabel = UILabel()

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = LiteColors.bg

        let card = UIView()
        card.backgroundColor = LiteColors.surface
        card.layer.cornerRadius = 22
        card.layer.borderColor = LiteColors.outline.cgColor
        card.layer.borderWidth = 1
        card.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(card)

        let logoBadge = UIView()
        logoBadge.backgroundColor = LiteColors.surfaceVariant
        logoBadge.layer.cornerRadius = 20
        logoBadge.translatesAutoresizingMaskIntoConstraints = false

        let logoLabel = UILabel()
        logoLabel.text = "SW"
        logoLabel.font = .boldSystemFont(ofSize: 28)
        logoLabel.textColor = LiteColors.neonCoral
        logoLabel.translatesAutoresizingMaskIntoConstraints = false
        logoBadge.addSubview(logoLabel)

        let titleLabel = UILabel()
        titleLabel.text = "SocialWiew Lite"
        titleLabel.font = .boldSystemFont(ofSize: 28)
        titleLabel.textColor = LiteColors.textPrimary
        titleLabel.textAlignment = .center

        let subtitleLabel = UILabel()
        subtitleLabel.text = "iOS 12+ için optimize edilmiş hafif kısa video deneyimi"
        subtitleLabel.font = .systemFont(ofSize: 15)
        subtitleLabel.textColor = LiteColors.textSecondary
        subtitleLabel.textAlignment = .center
        subtitleLabel.numberOfLines = 0

        errorLabel.font = .systemFont(ofSize: 13)
        errorLabel.textColor = LiteColors.neonCoral
        errorLabel.textAlignment = .center
        errorLabel.numberOfLines = 0
        errorLabel.isHidden = true

        signInButton.setTitle("Google ile Giriş Yap", for: .normal)
        signInButton.setTitleColor(.white, for: .normal)
        signInButton.titleLabel?.font = .boldSystemFont(ofSize: 16)
        signInButton.backgroundColor = LiteColors.neonCoral
        signInButton.layer.cornerRadius = 14
        signInButton.heightAnchor.constraint(equalToConstant: 50).isActive = true
        signInButton.addTarget(self, action: #selector(handleSignIn), for: .touchUpInside)

        spinner.hidesWhenStopped = true

        let stack = UIStackView(arrangedSubviews: [logoBadge, titleLabel, subtitleLabel, errorLabel, signInButton, spinner])
        stack.axis = .vertical
        stack.alignment = .center
        stack.spacing = 16
        stack.translatesAutoresizingMaskIntoConstraints = false
        card.addSubview(stack)

        NSLayoutConstraint.activate([
            logoBadge.widthAnchor.constraint(equalToConstant: 72),
            logoBadge.heightAnchor.constraint(equalToConstant: 72),
            logoLabel.centerXAnchor.constraint(equalTo: logoBadge.centerXAnchor),
            logoLabel.centerYAnchor.constraint(equalTo: logoBadge.centerYAnchor),
            signInButton.widthAnchor.constraint(equalTo: stack.widthAnchor),
            card.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 24),
            card.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -24),
            card.centerYAnchor.constraint(equalTo: view.centerYAnchor),
            stack.topAnchor.constraint(equalTo: card.topAnchor, constant: 28),
            stack.bottomAnchor.constraint(equalTo: card.bottomAnchor, constant: -28),
            stack.leadingAnchor.constraint(equalTo: card.leadingAnchor, constant: 20),
            stack.trailingAnchor.constraint(equalTo: card.trailingAnchor, constant: -20)
        ])
    }

    @objc private func handleSignIn() {
        guard let clientID = FirebaseApp.app()?.options.clientID else {
            showError("GoogleService-Info.plist dosyasında CLIENT_ID bulunamadı.")
            return
        }

        errorLabel.isHidden = true
        signInButton.isEnabled = false
        spinner.startAnimating()

        GIDSignIn.sharedInstance.configuration = GIDConfiguration(clientID: clientID)
        GIDSignIn.sharedInstance.signIn(withPresenting: self) { [weak self] result, error in
            guard let self else { return }
            if let error {
                self.finishWithError(error.localizedDescription)
                return
            }
            guard let user = result?.user,
                  let idToken = user.idToken?.tokenString else {
                self.finishWithError("Google kimlik belirteci alınamadı.")
                return
            }

            let credential = GoogleAuthProvider.credential(
                withIDToken: idToken,
                accessToken: user.accessToken.tokenString
            )
            Auth.auth().signIn(with: credential) { _, authError in
                if let authError {
                    self.finishWithError(authError.localizedDescription)
                } else {
                    self.spinner.stopAnimating()
                    self.signInButton.isEnabled = true
                }
            }
        }
    }

    private func finishWithError(_ msg: String) {
        spinner.stopAnimating()
        signInButton.isEnabled = true
        showError(msg)
    }

    private func showError(_ msg: String) {
        errorLabel.text = msg
        errorLabel.isHidden = false
    }
}
