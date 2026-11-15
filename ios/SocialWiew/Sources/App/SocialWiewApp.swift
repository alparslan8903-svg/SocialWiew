import FirebaseCore
import GoogleSignIn
import SwiftUI

final class SocialWiewAppDelegate: NSObject, UIApplicationDelegate {
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        if FirebaseApp.app() == nil {
            FirebaseApp.configure()
        }
        return true
    }

    func application(
        _ app: UIApplication,
        open url: URL,
        options: [UIApplication.OpenURLOptionsKey: Any] = [:]
    ) -> Bool {
        GIDSignIn.sharedInstance.handle(url)
    }
}

enum SocialWiewTab: Hashable {
    case feed
    case discover
    case search
    case upload
    case profile
}

@main
struct SocialWiewApp: App {
    @UIApplicationDelegateAdaptor(SocialWiewAppDelegate.self) private var delegate
    @StateObject private var viewModel = SocialFeedViewModel()

    var body: some Scene {
        WindowGroup {
            Group {
                if viewModel.currentUser == nil {
                    AuthView()
                } else {
                    MainTabScaffold(viewModel: viewModel)
                }
            }
            .preferredColorScheme(viewModel.themeMode.colorScheme)
        }
    }
}

private struct MainTabScaffold: View {
    @ObservedObject var viewModel: SocialFeedViewModel
    @State private var selectedTab: SocialWiewTab = .feed
    @State private var fullScreenVideo: VideoPost?
    @State private var selectedCreatorId: String?

    var body: some View {
        ZStack(alignment: .top) {
            TabView(selection: $selectedTab) {
                FeedView(
                    viewModel: viewModel,
                    onOpenCreatorProfile: { uid in
                        if uid == viewModel.currentUser?.uid {
                            selectedTab = .profile
                        } else {
                            selectedCreatorId = uid
                        }
                    },
                    onNavigateToUpload: { selectedTab = .upload },
                    onNavigateToDiscover: { selectedTab = .discover }
                )
                .tabItem {
                    Label("Akış", systemImage: "house.fill")
                }
                .tag(SocialWiewTab.feed)

                DiscoverView(
                    viewModel: viewModel,
                    onOpenCreatorProfile: { uid in
                        if uid == viewModel.currentUser?.uid {
                            selectedTab = .profile
                        } else {
                            selectedCreatorId = uid
                        }
                    },
                    onOpenVideoPlayer: { video in fullScreenVideo = video }
                )
                .tabItem {
                    Label("Keşfet", systemImage: "safari.fill")
                }
                .tag(SocialWiewTab.discover)

                SearchView(
                    viewModel: viewModel,
                    onOpenCreatorProfile: { uid in
                        if uid == viewModel.currentUser?.uid {
                            selectedTab = .profile
                        } else {
                            selectedCreatorId = uid
                        }
                    },
                    onOpenVideoPlayer: { video in fullScreenVideo = video }
                )
                .tabItem {
                    Label("Ara", systemImage: "magnifyingglass")
                }
                .tag(SocialWiewTab.search)

                UploadView(viewModel: viewModel) {
                    selectedTab = .feed
                }
                .tabItem {
                    Label("Yükle", systemImage: "plus.circle.fill")
                }
                .tag(SocialWiewTab.upload)

                NavigationStack {
                    ProfileView(
                        viewModel: viewModel,
                        userId: viewModel.currentUser?.uid ?? "",
                        onOpenVideoPlayer: { video in fullScreenVideo = video }
                    )
                }
                .tabItem {
                    Label("Profil", systemImage: "person.fill")
                }
                .tag(SocialWiewTab.profile)
            }
            .tint(SocialWiewColors.neonCoral)

            if let toast = viewModel.toastMessage {
                Text(toast)
                    .font(.caption.bold())
                    .padding(.horizontal, 14)
                    .padding(.vertical, 10)
                    .background(.ultraThinMaterial)
                    .clipShape(Capsule())
                    .padding(.top, 8)
                    .transition(.move(edge: .top).combined(with: .opacity))
            }
        }
        .sheet(item: $viewModel.selectedVideoForComments) { video in
            CommentsSheetView(viewModel: viewModel, video: video)
        }
        .sheet(item: Binding(
            get: { selectedCreatorId.map { CreatorDestination(id: $0) } },
            set: { selectedCreatorId = $0?.id }
        )) { dest in
            NavigationStack {
                ProfileView(
                    viewModel: viewModel,
                    userId: dest.id,
                    onOpenVideoPlayer: { video in
                        selectedCreatorId = nil
                        fullScreenVideo = video
                    }
                )
            }
        }
        .fullScreenCover(item: $fullScreenVideo) { video in
            ZStack(alignment: .topLeading) {
                VideoPlayerView(
                    video: video,
                    isActive: true,
                    isMuted: false,
                    onResolveCloudVideo: viewModel.resolveCloudVideoURL
                )
                .ignoresSafeArea()

                Button {
                    fullScreenVideo = nil
                } label: {
                    Image(systemName: "xmark")
                        .font(.headline)
                        .foregroundStyle(.white)
                        .padding(12)
                        .background(.black.opacity(0.6))
                        .clipShape(Circle())
                }
                .padding()
            }
        }
    }
}

private struct CreatorDestination: Identifiable {
    let id: String
}
