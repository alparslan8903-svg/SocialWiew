package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.GifBox
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.VideoPost
import com.example.ui.components.CreatorAvatar
import com.example.ui.components.VerifiedAdminBadge
import com.example.ui.components.VerifiedTickIcon
import com.example.ui.components.VideoMediaHelper
import com.example.ui.components.VideoPlayerSurface
import com.example.ui.viewmodel.UiState

@Composable
fun FeedScreen(
    videosState: UiState<List<VideoPost>>,
    currentUserId: String,
    verifiedUserIds: Set<String> = emptySet(),
    followingIds: Set<String>,
    likedVideoIds: Set<String>,
    onToggleLike: (VideoPost) -> Unit,
    onToggleFollow: (String) -> Unit,
    onOpenComments: (VideoPost) -> Unit,
    onOpenCreatorProfile: (String) -> Unit,
    onNavigateToUpload: () -> Unit,
    onNavigateToDiscover: () -> Unit,
    onNavigateToSearch: () -> Unit = {},
    onResolveCloudVideo: (suspend (String) -> Uri?)? = null
) {
    var selectedFeedTab by remember { mutableIntStateOf(0) } // 0 = For You, 1 = Following
    var isMuted by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF06070B))
    ) {
        when (videosState) {
            is UiState.Loading -> {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            is UiState.Error -> {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = videosState.message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center
                    )
                }
            }
            is UiState.Success -> {
                val filteredVideos = remember(videosState.data, selectedFeedTab, followingIds) {
                    if (selectedFeedTab == 1) {
                        videosState.data.filter { followingIds.contains(it.userId) }
                    } else {
                        videosState.data
                    }
                }

                if (filteredVideos.isEmpty()) {
                    EmptyFeedView(
                        isFollowingTab = selectedFeedTab == 1,
                        onNavigateToUpload = onNavigateToUpload,
                        onNavigateToDiscover = onNavigateToDiscover,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    val pagerState = rememberPagerState(pageCount = { filteredVideos.size })
                    VerticalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize()
                    ) { page ->
                        val video = filteredVideos[page]
                        val isActive = pagerState.currentPage == page
                        VideoFeedPage(
                            video = video,
                            isActive = isActive,
                            isMuted = isMuted,
                            isOwnVideo = video.userId == currentUserId,
                            isAuthorVerified = video.authorVerified || verifiedUserIds.contains(video.userId),
                            isFollowingCreator = followingIds.contains(video.userId),
                            isLiked = likedVideoIds.contains(video.id),
                            onToggleLike = { onToggleLike(video) },
                            onToggleFollow = { onToggleFollow(video.userId) },
                            onOpenComments = { onOpenComments(video) },
                            onOpenCreator = { onOpenCreatorProfile(video.userId) },
                            onResolveCloudVideo = onResolveCloudVideo
                        )
                    }
                }
            }
        }

        // Top Floating Tab Bar (Search + For You / Following + Mute)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.48f)
            ) {
                IconButton(
                    onClick = onNavigateToSearch,
                    modifier = Modifier.testTag("feed_search_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = "Ara",
                        tint = Color.White
                    )
                }
            }

            Surface(
                color = Color.Black.copy(alpha = 0.48f),
                shape = RoundedCornerShape(24.dp)
            ) {
                Row(modifier = Modifier.padding(4.dp)) {
                    FeedTabPill(
                        label = stringResource(id = R.string.tab_for_you),
                        selected = selectedFeedTab == 0,
                        onClick = { selectedFeedTab = 0 },
                        modifier = Modifier.testTag("feed_tab_for_you")
                    )
                    FeedTabPill(
                        label = stringResource(id = R.string.tab_following),
                        selected = selectedFeedTab == 1,
                        onClick = { selectedFeedTab = 1 },
                        modifier = Modifier.testTag("feed_tab_following")
                    )
                }
            }

            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.48f)
            ) {
                IconButton(
                    onClick = { isMuted = !isMuted },
                    modifier = Modifier.testTag("mute_toggle_button")
                ) {
                    Icon(
                        imageVector = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff
                        else Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = if (isMuted) "Sesi aç" else "Sesi kapat",
                        tint = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun FeedTabPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) Color.White else Color.White.copy(alpha = 0.78f),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VideoFeedPage(
    video: VideoPost,
    isActive: Boolean,
    isMuted: Boolean,
    isOwnVideo: Boolean,
    isAuthorVerified: Boolean = video.authorVerified,
    isFollowingCreator: Boolean,
    isLiked: Boolean,
    onToggleLike: () -> Unit,
    onToggleFollow: () -> Unit,
    onOpenComments: () -> Unit,
    onOpenCreator: () -> Unit,
    onResolveCloudVideo: (suspend (String) -> Uri?)? = null
) {
    val context = LocalContext.current
    val videoUri = remember(video.id, video.videoUrl, video.localFileName) {
        VideoMediaHelper.resolvePlayableUri(context, video)
    }
    val likeScale by animateFloatAsState(
        targetValue = if (isLiked) 1.18f else 1f,
        animationSpec = spring(dampingRatio = 0.45f),
        label = "likeScale"
    )

    val shareAction: () -> Unit = {
        val shareText = buildString {
            append("SocialWiew'de @${video.authorUsername} videosunu izle!")
            if (video.caption.isNotBlank()) append("\n${video.caption}")
            if (video.videoUrl.isNotBlank()) append("\n${video.videoUrl}")
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareText)
        }
        context.startActivity(Intent.createChooser(intent, "Videoyu Paylaş"))
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isTabletExpanded = maxWidth >= 840.dp
        val isMediumOrLarger = maxWidth >= 600.dp

        if (isTabletExpanded) {
            // Tablet Studio Split View: Centered 9:16 Theater Stage + Supporting Details Pane
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 32.dp, vertical = 20.dp)
                    .padding(top = 56.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight(0.92f)
                        .aspectRatio(9f / 16f)
                        .widthIn(max = 440.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .border(
                            width = 1.dp,
                            color = Color.White.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(28.dp)
                        )
                        .background(Color.Black)
                ) {
                    VideoPlayerSurface(
                        videoUri = videoUri,
                        thumbnailBase64 = video.thumbnailData,
                        isActive = isActive,
                        isMuted = isMuted,
                        modifier = Modifier.fillMaxSize(),
                        cloudVideoId = video.id,
                        cloudChunkCount = video.chunkCount,
                        onFetchCloudVideo = onResolveCloudVideo
                    )
                }

                Spacer(modifier = Modifier.width(28.dp))

                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                    shape = RoundedCornerShape(28.dp),
                    tonalElevation = 4.dp,
                    modifier = Modifier
                        .widthIn(min = 300.dp, max = 380.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(22.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { onOpenCreator() }
                                .padding(4.dp)
                        ) {
                            CreatorAvatar(
                                name = video.authorName,
                                avatarUrl = video.authorAvatarUrl,
                                size = 52.dp,
                                modifier = Modifier.testTag("video_author_avatar")
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = video.authorName.ifBlank { video.authorUsername },
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    if (isAuthorVerified) {
                                        Spacer(modifier = Modifier.width(5.dp))
                                        VerifiedTickIcon(size = 18.dp)
                                    }
                                }
                                Text(
                                    text = "@${video.authorUsername}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.secondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (isAuthorVerified) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    VerifiedAdminBadge(label = "Admin")
                                }
                            }
                        }

                        if (!isOwnVideo) {
                            Button(
                                onClick = onToggleFollow,
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isFollowingCreator) MaterialTheme.colorScheme.surfaceVariant
                                    else MaterialTheme.colorScheme.primary,
                                    contentColor = if (isFollowingCreator) MaterialTheme.colorScheme.onSurface
                                    else Color.White
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("quick_follow_button")
                            ) {
                                Text(
                                    text = if (isFollowingCreator) stringResource(id = R.string.following)
                                    else stringResource(id = R.string.follow)
                                )
                            }
                        }

                        if (video.caption.isNotBlank()) {
                            Text(
                                text = video.caption,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        if (video.tags.isNotEmpty()) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                video.tags.forEach { tag ->
                                    Surface(
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text(
                                            text = "#$tag",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            FilledTonalButton(
                                onClick = onToggleLike,
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = if (isLiked) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceVariant
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("video_like_button")
                            ) {
                                Icon(
                                    imageVector = if (isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                    contentDescription = "Beğen",
                                    tint = if (isLiked) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.scale(likeScale).size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("${video.likesCount}")
                            }

                            OutlinedButton(
                                onClick = shareAction,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("video_share_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Share,
                                    contentDescription = "Videoyu paylaş",
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Paylaş")
                            }
                        }

                        Button(
                            onClick = onOpenComments,
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondary,
                                contentColor = Color.Black
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("video_comment_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.GifBox,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Yorumlar & Tenor GIF (${video.commentsCount})",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        } else {
            // Compact & Medium (centered portrait stage on medium tablets, full-bleed on phones)
            Box(
                modifier = Modifier
                    .then(
                        if (isMediumOrLarger) {
                            Modifier
                                .align(Alignment.Center)
                                .padding(top = 64.dp, bottom = 20.dp)
                                .fillMaxHeight(0.94f)
                                .widthIn(max = 460.dp)
                                .clip(RoundedCornerShape(26.dp))
                        } else {
                            Modifier.fillMaxSize()
                        }
                    )
            ) {
                VideoPlayerSurface(
                    videoUri = videoUri,
                    thumbnailBase64 = video.thumbnailData,
                    isActive = isActive,
                    isMuted = isMuted,
                    modifier = Modifier.fillMaxSize(),
                    cloudVideoId = video.id,
                    cloudChunkCount = video.chunkCount,
                    onFetchCloudVideo = onResolveCloudVideo
                )

                // Bottom readability scrim
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                            )
                        )
                )

                // Right-side action rail
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 14.dp, bottom = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    Box(contentAlignment = Alignment.BottomCenter) {
                        CreatorAvatar(
                            name = video.authorName,
                            avatarUrl = video.authorAvatarUrl,
                            size = 48.dp,
                            modifier = Modifier
                                .clickable { onOpenCreator() }
                                .testTag("video_author_avatar")
                        )
                        if (!isOwnVideo) {
                            Surface(
                                shape = CircleShape,
                                color = if (isFollowingCreator) MaterialTheme.colorScheme.secondary
                                else MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .padding(top = 36.dp)
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .clickable { onToggleFollow() }
                                    .testTag("quick_follow_button")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (isFollowingCreator) Icons.Filled.Check else Icons.Filled.Add,
                                        contentDescription = if (isFollowingCreator) "Takip ediliyor" else "Takip et",
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }

                    ActionRailButton(
                        icon = if (isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        tint = if (isLiked) MaterialTheme.colorScheme.primary else Color.White,
                        countText = video.likesCount.toString(),
                        contentDescription = "Beğen",
                        onClick = onToggleLike,
                        iconModifier = Modifier.scale(likeScale),
                        modifier = Modifier.testTag("video_like_button")
                    )

                    ActionRailButton(
                        icon = Icons.Filled.ChatBubble,
                        tint = Color.White,
                        countText = video.commentsCount.toString(),
                        contentDescription = "Yorumlar",
                        onClick = onOpenComments,
                        modifier = Modifier.testTag("video_comment_button")
                    )

                    ActionRailButton(
                        icon = Icons.Filled.Share,
                        tint = Color.White,
                        countText = "Paylaş",
                        contentDescription = "Videoyu paylaş",
                        onClick = shareAction,
                        modifier = Modifier.testTag("video_share_button")
                    )
                }

                // Bottom-left caption & tags
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth(0.78f)
                        .padding(start = 16.dp, bottom = 24.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { onOpenCreator() }
                    ) {
                        Text(
                            text = "@${video.authorUsername}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        if (isAuthorVerified) {
                            Spacer(modifier = Modifier.width(5.dp))
                            VerifiedTickIcon(size = 17.dp)
                        }
                        if (!isOwnVideo) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                color = if (isFollowingCreator) Color.White.copy(alpha = 0.2f)
                                else MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.clickable { onToggleFollow() }
                            ) {
                                Text(
                                    text = if (isFollowingCreator) stringResource(id = R.string.following)
                                    else stringResource(id = R.string.follow),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }

                    if (video.caption.isNotBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = video.caption,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.95f),
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (video.tags.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            video.tags.forEach { tag ->
                                Surface(
                                    color = Color.White.copy(alpha = 0.16f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = "#$tag",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionRailButton(
    icon: ImageVector,
    tint: Color,
    countText: String,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconModifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clickable(onClick = onClick)
    ) {
        Surface(
            shape = CircleShape,
            color = Color.Black.copy(alpha = 0.42f),
            modifier = Modifier.size(48.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    tint = tint,
                    modifier = iconModifier.size(26.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = countText,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun EmptyFeedView(
    isFollowingTab: Boolean,
    onNavigateToUpload: () -> Unit,
    onNavigateToDiscover: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(id = R.drawable.img_empty_feed),
            contentDescription = "Boş video akışı",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(180.dp)
                .clip(RoundedCornerShape(28.dp))
        )
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = if (isFollowingTab) "Henüz kimseyi takip etmiyorsun"
            else "Akış henüz sessiz",
            style = MaterialTheme.typography.headlineSmall,
            color = Color.White,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (isFollowingTab) "Keşfet sekmesinden yeni içerik üreticilerini takip ederek videolarını burada görebilirsin."
            else "Topluluğun ilk kısa videosunu paylaşarak SocialWiew'i başlat!",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.72f),
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 420.dp)
        )
        Spacer(modifier = Modifier.height(22.dp))
        Button(
            onClick = if (isFollowingTab) onNavigateToDiscover else onNavigateToUpload,
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White
            ),
            modifier = Modifier.height(50.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Upload,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isFollowingTab) "İçerik Üreticilerini Keşfet" else "İlk Videoyu Yükle",
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}
