import UIKit
import FirebaseAuth
import FirebaseCore
import GoogleSignIn

@UIApplicationMain
final class AppDelegate: UIResponder, UIApplicationDelegate {
    var window: UIWindow?
    private var authHandle: AuthStateDidChangeListenerHandle?

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?
    ) -> Bool {
        if FirebaseApp.app() == nil {
            FirebaseApp.configure()
        }

        let window = UIWindow(frame: UIScreen.main.bounds)
        window.backgroundColor = LiteColors.bg
        self.window = window

        authHandle = Auth.auth().addStateDidChangeListener { [weak self] _, user in
            self?.updateRoot(for: user)
        }
        updateRoot(for: Auth.auth().currentUser)
        window.makeKeyAndVisible()
        return true
    }

    func application(
        _ app: UIApplication,
        open url: URL,
        options: [UIApplication.OpenURLOptionsKey: Any] = [:]
    ) -> Bool {
        GIDSignIn.sharedInstance.handle(url)
    }

    private func updateRoot(for user: User?) {
        guard let window else { return }
        if user == nil {
            LiteAppState.shared.stopSession()
            if !(window.rootViewController is LiteAuthViewController) {
                window.rootViewController = LiteAuthViewController()
            }
        } else {
            LiteAppState.shared.startSession()
            if !(window.rootViewController is LiteMainTabBarController) {
                window.rootViewController = LiteMainTabBarController()
            }
        }
    }
}

final class LiteMainTabBarController: UITabBarController {
    override func viewDidLoad() {
        super.viewDidLoad()

        tabBar.barTintColor = LiteColors.surface
        tabBar.tintColor = LiteColors.neonCoral
        tabBar.unselectedItemTintColor = LiteColors.textSecondary
        tabBar.isTranslucent = false

        let feedVC = LiteFeedViewController()
        feedVC.tabBarItem = UITabBarItem(
            title: "Akış",
            image: LiteIcons.icon(.feed, size: 22, color: LiteColors.textSecondary),
            selectedImage: LiteIcons.icon(.feed, size: 22, color: LiteColors.neonCoral)
        )

        let discoverVC = UINavigationController(rootViewController: LiteDiscoverViewController())
        configureNav(discoverVC)
        discoverVC.tabBarItem = UITabBarItem(
            title: "Keşfet",
            image: LiteIcons.icon(.discover, size: 22, color: LiteColors.textSecondary),
            selectedImage: LiteIcons.icon(.discover, size: 22, color: LiteColors.neonCoral)
        )

        let searchVC = UINavigationController(rootViewController: LiteSearchViewController())
        configureNav(searchVC)
        searchVC.tabBarItem = UITabBarItem(
            title: "Ara",
            image: LiteIcons.icon(.search, size: 22, color: LiteColors.textSecondary),
            selectedImage: LiteIcons.icon(.search, size: 22, color: LiteColors.neonCoral)
        )

        let uploadVC = UINavigationController(rootViewController: LiteUploadViewController())
        configureNav(uploadVC)
        uploadVC.tabBarItem = UITabBarItem(
            title: "Yükle",
            image: LiteIcons.icon(.upload, size: 22, color: LiteColors.textSecondary),
            selectedImage: LiteIcons.icon(.upload, size: 22, color: LiteColors.neonCoral)
        )

        let profileVC = UINavigationController(rootViewController: LiteProfileViewController(userId: nil))
        configureNav(profileVC)
        profileVC.tabBarItem = UITabBarItem(
            title: "Profil",
            image: LiteIcons.icon(.profile, size: 22, color: LiteColors.textSecondary),
            selectedImage: LiteIcons.icon(.profile, size: 22, color: LiteColors.neonCoral)
        )

        viewControllers = [feedVC, discoverVC, searchVC, uploadVC, profileVC]
    }

    private func configureNav(_ nav: UINavigationController) {
        nav.navigationBar.barTintColor = LiteColors.surface
        nav.navigationBar.tintColor = LiteColors.neonCoral
        nav.navigationBar.isTranslucent = false
        nav.navigationBar.titleTextAttributes = [.foregroundColor: LiteColors.textPrimary]
    }
}
