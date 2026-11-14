package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.data.model.VideoPost
import com.example.data.repository.SocialRepository
import com.example.data.repository.TenorRepository
import com.example.data.repository.ThemeMode
import com.example.data.repository.ThemePreferencesRepository
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.authStateFlow
import com.example.ui.auth.signOutUser
import com.example.ui.components.CommentsBottomSheet
import com.example.ui.screens.DiscoverScreen
import com.example.ui.screens.FeedScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.SearchScreen
import com.example.ui.screens.UploadScreen
import com.example.ui.screens.VideoFeedPage
import com.example.ui.theme.VibeReelTheme
import com.example.ui.viewmodel.SocialFeedViewModel
import com.example.ui.viewmodel.UiState
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

object VibeRoutes {
    const val FEED = "feed"
    const val DISCOVER = "discover"
    const val SEARCH = "search"
    const val UPLOAD = "upload"
    const val PROFILE = "profile"
    const val CREATOR_PROFILE = "creator/{userId}"
    fun creatorRoute(userId: String) = "creator/$userId"
}

private data class NavTabItem(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

private val TopTabs = listOf(
    NavTabItem(VibeRoutes.FEED, R.string.nav_feed, Icons.Filled.Home, Icons.Outlined.Home, "nav_feed"),
    NavTabItem(VibeRoutes.DISCOVER, R.string.nav_discover, Icons.Filled.Explore, Icons.Outlined.Explore, "nav_discover"),
    NavTabItem(VibeRoutes.SEARCH, R.string.nav_search, Icons.Filled.Search, Icons.Outlined.Search, "nav_search"),
    NavTabItem(VibeRoutes.UPLOAD, R.string.nav_upload, Icons.Filled.AddCircle, Icons.Outlined.AddCircleOutline, "nav_upload"),
    NavTabItem(VibeRoutes.PROFILE, R.string.nav_profile, Icons.Filled.Person, Icons.Outlined.Person, "nav_profile")
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            val themeRepo = remember(context) { ThemePreferencesRepository(context.applicationContext) }
            val themeMode by themeRepo.themeMode.collectAsStateWithLifecycle(initialValue = ThemeMode.DARK)
            val coroutineScope = rememberCoroutineScope()

            val isDark = when (themeMode) {
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }

            VibeReelTheme(darkTheme = isDark) {
                AppNavigation(
                    themeMode = themeMode,
                    onChangeThemeMode = { mode ->
                        coroutineScope.launch { themeRepo.setThemeMode(mode) }
                    }
                )
            }
        }
    }
}

@Composable
fun AppNavigation(
    themeMode: ThemeMode,
    onChangeThemeMode: (ThemeMode) -> Unit
) {
    val auth = remember { Firebase.auth }
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser

    if (user == null) {
        AuthScreen(onAuthSuccess = {})
    } else {
        key(user.uid) {
            MainSocialScaffold(
                currentUserId = user.uid,
                themeMode = themeMode,
                onChangeThemeMode = onChangeThemeMode
            )
        }
    }
}

@Composable
fun MainSocialScaffold(
    currentUserId: String,
    themeMode: ThemeMode,
    onChangeThemeMode: (ThemeMode) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val viewModel: SocialFeedViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                SocialFeedViewModel(
                    socialRepository = SocialRepository(db, Firebase.auth),
                    tenorRepository = TenorRepository(),
                    currentUserId = currentUserId
                )
            }
        }
    )

    val videosState by viewModel.videosState.collectAsStateWithLifecycle()
    val creatorsState by viewModel.creatorsState.collectAsStateWithLifecycle()
    val followingIds by viewModel.followingIds.collectAsStateWithLifecycle()
    val likedVideoIds by viewModel.likedVideoIds.collectAsStateWithLifecycle()
    val selectedVideoForComments by viewModel.selectedVideoForComments.collectAsStateWithLifecycle()
    val commentsState by viewModel.activeCommentsState.collectAsStateWithLifecycle()
    val tenorState by viewModel.tenorState.collectAsStateWithLifecycle()
    val isPublishing by viewModel.isPublishing.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) {
        viewModel.toastEvents.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    var fullScreenVideo by remember { mutableStateOf<VideoPost?>(null) }

    val navigateToTab: (String) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                if (!useRail && fullScreenVideo == null) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
                    ) {
                        TopTabs.forEach { item ->
                            val selected = currentRoute == item.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateToTab(item.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = stringResource(id = item.labelRes)
                                    )
                                },
                                label = { Text(stringResource(id = item.labelRes)) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer
                                ),
                                modifier = Modifier.testTag(item.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail && fullScreenVideo == null) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface,
                        header = {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(top = 12.dp, bottom = 16.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = Color.White,
                                    shadowElevation = 3.dp
                                ) {
                                    Image(
                                        painter = painterResource(id = R.drawable.ic_socialwiew_logo),
                                        contentDescription = stringResource(id = R.string.app_name),
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(40.dp)
                                            .padding(3.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                    )
                                }
                            }
                        }
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            TopTabs.forEach { item ->
                                val selected = currentRoute == item.route
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = { navigateToTab(item.route) },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                            contentDescription = stringResource(id = item.labelRes)
                                        )
                                    },
                                    label = { Text(stringResource(id = item.labelRes)) },
                                    alwaysShowLabel = true,
                                    colors = NavigationRailItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.primary,
                                        selectedTextColor = MaterialTheme.colorScheme.primary,
                                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                                    ),
                                    modifier = Modifier.testTag(item.testTag)
                                )
                            }
                        }
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = VibeRoutes.FEED,
                    modifier = Modifier.weight(1f)
                ) {
                    composable(VibeRoutes.FEED) {
                        FeedScreen(
                            videosState = videosState,
                            currentUserId = currentUserId,
                            followingIds = followingIds,
                            likedVideoIds = likedVideoIds,
                            onToggleLike = viewModel::toggleLike,
                            onToggleFollow = viewModel::toggleFollow,
                            onOpenComments = viewModel::openComments,
                            onOpenCreatorProfile = { uid ->
                                if (uid == currentUserId) navigateToTab(VibeRoutes.PROFILE)
                                else navController.navigate(VibeRoutes.creatorRoute(uid))
                            },
                            onNavigateToUpload = { navigateToTab(VibeRoutes.UPLOAD) },
                            onNavigateToDiscover = { navigateToTab(VibeRoutes.DISCOVER) },
                            onNavigateToSearch = { navigateToTab(VibeRoutes.SEARCH) },
                            onResolveCloudVideo = { videoId ->
                                viewModel.resolveCloudVideoUri(context, videoId)
                            }
                        )
                    }

                    composable(VibeRoutes.DISCOVER) {
                        DiscoverScreen(
                            creatorsState = creatorsState,
                            videosState = videosState,
                            currentUserId = currentUserId,
                            followingIds = followingIds,
                            onToggleFollow = viewModel::toggleFollow,
                            onOpenCreatorProfile = { uid ->
                                if (uid == currentUserId) navigateToTab(VibeRoutes.PROFILE)
                                else navController.navigate(VibeRoutes.creatorRoute(uid))
                            },
                            onOpenVideoPlayer = { video -> fullScreenVideo = video },
                            onNavigateToSearch = { navigateToTab(VibeRoutes.SEARCH) }
                        )
                    }

                    composable(VibeRoutes.SEARCH) {
                        SearchScreen(
                            creatorsState = creatorsState,
                            videosState = videosState,
                            currentUserId = currentUserId,
                            followingIds = followingIds,
                            onToggleFollow = viewModel::toggleFollow,
                            onOpenCreatorProfile = { uid ->
                                if (uid == currentUserId) navigateToTab(VibeRoutes.PROFILE)
                                else navController.navigate(VibeRoutes.creatorRoute(uid))
                            },
                            onOpenVideoPlayer = { video -> fullScreenVideo = video }
                        )
                    }

                    composable(VibeRoutes.UPLOAD) {
                        UploadScreen(
                            isPublishing = isPublishing,
                            onPublishVideo = { caption, url, localName, localPath, thumb, duration, tags, onDone ->
                                viewModel.publishVideo(
                                    caption = caption,
                                    videoUrl = url,
                                    localFileName = localName,
                                    localFilePath = localPath,
                                    thumbnailData = thumb,
                                    durationMs = duration,
                                    tags = tags
                                ) {
                                    onDone()
                                    navigateToTab(VibeRoutes.FEED)
                                }
                            }
                        )
                    }

                    composable(VibeRoutes.PROFILE) {
                        ProfileScreen(
                            userId = currentUserId,
                            currentUserId = currentUserId,
                            isFollowing = false,
                            themeMode = themeMode,
                            onChangeThemeMode = onChangeThemeMode,
                            onToggleFollow = {},
                            onUpdateProfile = viewModel::updateProfile,
                            onDeleteVideo = viewModel::deleteVideo,
                            onOpenVideoPlayer = { video -> fullScreenVideo = video },
                            onSignOut = {
                                signOutUser(credentialManager, coroutineScope)
                            }
                        )
                    }

                    composable(
                        route = VibeRoutes.CREATOR_PROFILE,
                        arguments = listOf(navArgument("userId") { type = NavType.StringType })
                    ) { backStackEntry ->
                        val targetUserId = backStackEntry.arguments?.getString("userId") ?: return@composable
                        ProfileScreen(
                            userId = targetUserId,
                            currentUserId = currentUserId,
                            isFollowing = followingIds.contains(targetUserId),
                            themeMode = themeMode,
                            onChangeThemeMode = onChangeThemeMode,
                            onToggleFollow = { viewModel.toggleFollow(targetUserId) },
                            onUpdateProfile = viewModel::updateProfile,
                            onDeleteVideo = viewModel::deleteVideo,
                            onOpenVideoPlayer = { video -> fullScreenVideo = video },
                            onSignOut = {
                                signOutUser(credentialManager, coroutineScope)
                            },
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }

        // Full-Screen Video Modal when opened from Discover, Search, or Profile grids
        fullScreenVideo?.let { activeVideo ->
            val liveVideo = (videosState as? UiState.Success)?.data
                ?.firstOrNull { it.id == activeVideo.id } ?: activeVideo
            BackHandler { fullScreenVideo = null }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF06070B))
            ) {
                VideoFeedPage(
                    video = liveVideo,
                    isActive = selectedVideoForComments == null,
                    isMuted = false,
                    isOwnVideo = liveVideo.userId == currentUserId,
                    isFollowingCreator = followingIds.contains(liveVideo.userId),
                    isLiked = likedVideoIds.contains(liveVideo.id),
                    onToggleLike = { viewModel.toggleLike(liveVideo) },
                    onToggleFollow = { viewModel.toggleFollow(liveVideo.userId) },
                    onOpenComments = { viewModel.openComments(liveVideo) },
                    onOpenCreator = {
                        fullScreenVideo = null
                        if (liveVideo.userId == currentUserId) navigateToTab(VibeRoutes.PROFILE)
                        else navController.navigate(VibeRoutes.creatorRoute(liveVideo.userId))
                    },
                    onResolveCloudVideo = { videoId ->
                        viewModel.resolveCloudVideoUri(context, videoId)
                    }
                )
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.55f),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .statusBarsPadding()
                        .padding(16.dp)
                ) {
                    IconButton(onClick = { fullScreenVideo = null }) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Videoyu kapat",
                            tint = Color.White
                        )
                    }
                }
            }
        }

        selectedVideoForComments?.let { video ->
            val liveVideo = (videosState as? UiState.Success)?.data
                ?.firstOrNull { it.id == video.id } ?: video
            CommentsBottomSheet(
                video = liveVideo,
                currentUserId = currentUserId,
                commentsState = commentsState,
                tenorState = tenorState,
                onLoadTenorGifs = viewModel::loadTenorGifs,
                onSendComment = { text, gif ->
                    viewModel.addComment(liveVideo.id, text, gif)
                },
                onDeleteComment = { commentId ->
                    viewModel.deleteComment(liveVideo.id, commentId)
                },
                onDismiss = viewModel::closeComments
            )
        }
    }
}
