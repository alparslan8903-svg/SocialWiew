package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.SettingsBrightness
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.R
import com.example.data.model.UserPublic
import com.example.data.model.VideoPost
import com.example.data.repository.SocialRepository
import com.example.data.repository.ThemeMode
import com.example.ui.components.CreatorAvatar
import com.example.ui.viewmodel.ProfileUiState
import com.example.ui.viewmodel.ProfileViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

@Composable
fun ProfileScreen(
    userId: String,
    currentUserId: String,
    isFollowing: Boolean,
    themeMode: ThemeMode,
    onChangeThemeMode: (ThemeMode) -> Unit,
    onToggleFollow: () -> Unit,
    onUpdateProfile: (displayName: String, username: String, bio: String, onDone: () -> Unit) -> Unit,
    onDeleteVideo: (String) -> Unit,
    onOpenVideoPlayer: (VideoPost) -> Unit,
    onSignOut: () -> Unit,
    onNavigateBack: (() -> Unit)? = null
) {
    val profileViewModel: ProfileViewModel = viewModel(
        key = "profile_${currentUserId}_$userId",
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                ProfileViewModel(
                    socialRepository = SocialRepository(db, Firebase.auth),
                    profileUserId = userId
                )
            }
        }
    )

    val uiState by profileViewModel.uiState.collectAsStateWithLifecycle()
    val isOwnProfile = userId == currentUserId

    if (onNavigateBack != null) {
        BackHandler { onNavigateBack() }
    }

    when (val state = uiState) {
        is ProfileUiState.Loading -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .statusBarsPadding(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        }
        is ProfileUiState.Error -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .statusBarsPadding()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = state.message,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
            }
        }
        is ProfileUiState.Success -> {
            ProfileContent(
                profile = state.profile,
                videos = state.uploadedVideos,
                isOwnProfile = isOwnProfile,
                isFollowing = isFollowing,
                themeMode = themeMode,
                onChangeThemeMode = onChangeThemeMode,
                onToggleFollow = onToggleFollow,
                onUpdateProfile = onUpdateProfile,
                onDeleteVideo = onDeleteVideo,
                onOpenVideoPlayer = onOpenVideoPlayer,
                onSignOut = onSignOut,
                onNavigateBack = onNavigateBack
            )
        }
    }
}

@Composable
fun ProfileContent(
    profile: UserPublic?,
    videos: List<VideoPost>,
    isOwnProfile: Boolean,
    isFollowing: Boolean,
    themeMode: ThemeMode,
    onChangeThemeMode: (ThemeMode) -> Unit,
    onToggleFollow: () -> Unit,
    onUpdateProfile: (displayName: String, username: String, bio: String, onDone: () -> Unit) -> Unit,
    onDeleteVideo: (String) -> Unit,
    onOpenVideoPlayer: (VideoPost) -> Unit,
    onSignOut: () -> Unit,
    onNavigateBack: (() -> Unit)? = null
) {
    var showEditDialog by remember { mutableStateOf(false) }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 115.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 28.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .testTag("profile_screen_grid")
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (onNavigateBack != null) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Geri"
                            )
                        }
                    } else {
                        Text(
                            text = stringResource(id = R.string.nav_profile),
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }

                    if (isOwnProfile) {
                        IconButton(
                            onClick = onSignOut,
                            modifier = Modifier.testTag("sign_out_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Logout,
                                contentDescription = stringResource(id = R.string.sign_out),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                CreatorAvatar(
                    name = profile?.displayName ?: "S",
                    avatarUrl = profile?.avatarUrl ?: "",
                    size = 86.dp,
                    modifier = Modifier.testTag("profile_avatar")
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = profile?.displayName ?: "SocialWiew Kullanıcısı",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "@${profile?.username ?: "kullanici"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary
                )

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = profile?.bio?.takeIf { it.isNotBlank() }
                        ?: if (isOwnProfile) "Kendini tanıtmak için profilini düzenle."
                        else "Henüz biyografi eklenmemiş.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .padding(horizontal = 24.dp)
                        .testTag("profile_bio")
                )

                Spacer(modifier = Modifier.height(18.dp))

                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(20.dp),
                    tonalElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        ProfileStatColumn(label = "Takipçi", value = profile?.followersCount ?: 0L)
                        ProfileStatColumn(label = "Takip", value = profile?.followingCount ?: 0L)
                        ProfileStatColumn(label = "Video", value = videos.size.toLong())
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (isOwnProfile) {
                    OutlinedButton(
                        onClick = { showEditDialog = true },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("edit_profile_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = stringResource(id = R.string.edit_profile))
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "Görünüm Modu",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                ThemeMode.entries.forEach { mode ->
                                    val icon = when (mode) {
                                        ThemeMode.DARK -> Icons.Filled.DarkMode
                                        ThemeMode.LIGHT -> Icons.Filled.LightMode
                                        ThemeMode.SYSTEM -> Icons.Filled.SettingsBrightness
                                    }
                                    FilterChip(
                                        selected = themeMode == mode,
                                        onClick = { onChangeThemeMode(mode) },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = icon,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        },
                                        label = {
                                            Text(
                                                text = mode.labelTr,
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("theme_chip_${mode.name.lowercase()}")
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Button(
                        onClick = onToggleFollow,
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isFollowing) MaterialTheme.colorScheme.surfaceVariant
                            else MaterialTheme.colorScheme.primary,
                            contentColor = if (isFollowing) MaterialTheme.colorScheme.onSurface
                            else Color.White
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("profile_follow_button")
                    ) {
                        Text(
                            text = if (isFollowing) stringResource(id = R.string.following)
                            else stringResource(id = R.string.follow),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start
                ) {
                    Text(
                        text = "Paylaşılan Videolar",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        if (videos.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (isOwnProfile) "Henüz video paylaşmadınız."
                        else "Bu içerik üreticisinin henüz videosu yok.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(24.dp)
                    )
                }
            }
        } else {
            items(videos, key = { it.id }) { video ->
                Box {
                    VideoGridCard(
                        video = video,
                        onClick = { onOpenVideoPlayer(video) }
                    )
                    if (isOwnProfile) {
                        Surface(
                            shape = CircleShape,
                            color = Color.Black.copy(alpha = 0.6f),
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(6.dp)
                        ) {
                            IconButton(
                                onClick = { onDeleteVideo(video.id) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.DeleteOutline,
                                    contentDescription = "Videoyu sil",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showEditDialog && profile != null) {
        EditProfileDialog(
            initialDisplayName = profile.displayName,
            initialUsername = profile.username,
            initialBio = profile.bio,
            onDismiss = { showEditDialog = false },
            onSave = { name, handle, bio ->
                onUpdateProfile(name, handle, bio) {
                    showEditDialog = false
                }
            }
        )
    }
}

@Composable
private fun ProfileStatColumn(label: String, value: Long) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun EditProfileDialog(
    initialDisplayName: String,
    initialUsername: String,
    initialBio: String,
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit
) {
    var displayName by remember { mutableStateOf(initialDisplayName) }
    var username by remember { mutableStateOf(initialUsername) }
    var bio by remember { mutableStateOf(initialBio) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(id = R.string.edit_profile)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { if (it.length <= 50) displayName = it },
                    label = { Text("Görünen İsim") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = username,
                    onValueChange = { if (it.length <= 30) username = it },
                    label = { Text("Kullanıcı Adı") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = bio,
                    onValueChange = { if (it.length <= 200) bio = it },
                    label = { Text("Biyografi") },
                    minLines = 2,
                    maxLines = 3
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(displayName, username, bio) },
                enabled = displayName.isNotBlank() && username.trim().length >= 3
            ) {
                Text("Kaydet")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("İptal")
            }
        }
    )
}
