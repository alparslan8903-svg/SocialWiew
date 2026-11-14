package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.GifBox
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.TenorGifItem
import com.example.data.model.VideoComment
import com.example.data.model.VideoPost
import com.example.data.repository.SocialRepository
import com.example.data.repository.TenorUiState
import com.example.ui.viewmodel.UiState
import kotlinx.coroutines.delay

private val GifQuickMoods = listOf(
    "Trend" to "",
    "Alkış" to "clap",
    "Kahkaha" to "laugh",
    "Kalp" to "heart",
    "Şaşkın" to "wow",
    "Dans" to "dance"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentsBottomSheet(
    video: VideoPost,
    currentUserId: String,
    verifiedUserIds: Set<String> = emptySet(),
    commentsState: UiState<List<VideoComment>>,
    tenorState: TenorUiState,
    onLoadTenorGifs: (String) -> Unit,
    onSendComment: (text: String, gif: TenorGifItem?) -> Unit,
    onDeleteComment: (commentId: String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val gifImageLoader = rememberGifImageLoader()

    var commentText by remember { mutableStateOf("") }
    var showGifPicker by remember { mutableStateOf(false) }
    var gifSearchQuery by remember { mutableStateOf("") }
    var selectedMood by remember { mutableStateOf("Trend") }

    LaunchedEffect(showGifPicker, gifSearchQuery) {
        if (showGifPicker) {
            delay(280L)
            onLoadTenorGifs(gifSearchQuery)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        sheetMaxWidth = 580.dp,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .heightIn(min = 380.dp, max = 640.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${stringResource(id = R.string.comments_title)} (${video.commentsCount})",
                    style = MaterialTheme.typography.titleLarge
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("close_comments_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Yorumları kapat"
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (commentsState) {
                    is UiState.Loading -> {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    is UiState.Error -> {
                        Text(
                            text = commentsState.message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(20.dp)
                        )
                    }
                    is UiState.Success -> {
                        if (commentsState.data.isEmpty()) {
                            Column(
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.ChatBubbleOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(38.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Henüz yorum yok. İlk yorumu veya Tenor GIF'i sen at!",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            LazyColumn(
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(commentsState.data, key = { it.id }) { comment ->
                                    CommentItemRow(
                                        comment = comment,
                                        isOwner = comment.userId == currentUserId,
                                        isAuthorVerified = comment.authorVerified ||
                                            verifiedUserIds.contains(comment.userId) ||
                                            SocialRepository.isVerifiedUsername(comment.authorUsername),
                                        gifImageLoader = gifImageLoader,
                                        onDelete = { onDeleteComment(comment.id) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = showGifPicker,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                TenorGifPickerPanel(
                    query = gifSearchQuery,
                    selectedMood = selectedMood,
                    tenorState = tenorState,
                    gifImageLoader = gifImageLoader,
                    onQueryChange = {
                        gifSearchQuery = it
                        selectedMood = if (it.isBlank()) "Trend" else ""
                    },
                    onMoodSelected = { label, searchTerm ->
                        selectedMood = label
                        gifSearchQuery = searchTerm
                    },
                    onGifSelected = { gif ->
                        onSendComment(commentText.trim(), gif)
                        commentText = ""
                        showGifPicker = false
                    },
                    onRetry = { onLoadTenorGifs(gifSearchQuery) }
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { showGifPicker = !showGifPicker },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("toggle_gif_picker_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.GifBox,
                        contentDescription = "Tenor GIF Seçici",
                        tint = if (showGifPicker) MaterialTheme.colorScheme.secondary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(28.dp)
                    )
                }

                OutlinedTextField(
                    value = commentText,
                    onValueChange = { commentText = it },
                    placeholder = {
                        Text(
                            text = stringResource(id = R.string.comment_hint),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(22.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("comment_text_input")
                )

                Spacer(modifier = Modifier.width(8.dp))

                Surface(
                    shape = CircleShape,
                    color = if (commentText.isNotBlank()) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable(enabled = commentText.isNotBlank()) {
                            onSendComment(commentText.trim(), null)
                            commentText = ""
                        }
                        .testTag("send_comment_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Yorum gönder",
                            tint = if (commentText.isNotBlank()) Color.White
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CommentItemRow(
    comment: VideoComment,
    isOwner: Boolean,
    isAuthorVerified: Boolean,
    gifImageLoader: ImageLoader,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        CreatorAvatar(
            name = comment.authorName,
            avatarUrl = comment.authorAvatarUrl,
            size = 36.dp
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "@${comment.authorUsername}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (isAuthorVerified) {
                    Spacer(modifier = Modifier.width(4.dp))
                    VerifiedTickIcon(size = 14.dp)
                }
            }
            if (comment.text.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = comment.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            if (comment.gifUrl.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    AsyncImage(
                        model = comment.gifUrl,
                        imageLoader = gifImageLoader,
                        contentDescription = "Tenor GIF yorumu",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .widthIn(min = 96.dp, max = 230.dp)
                            .heightIn(min = 72.dp, max = 230.dp)
                            .clip(RoundedCornerShape(14.dp))
                    )
                }
            }
        }
        if (isOwner) {
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.DeleteOutline,
                    contentDescription = "Yorumu sil",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun TenorGifPickerPanel(
    query: String,
    selectedMood: String,
    tenorState: TenorUiState,
    gifImageLoader: ImageLoader,
    onQueryChange: (String) -> Unit,
    onMoodSelected: (label: String, query: String) -> Unit,
    onGifSelected: (TenorGifItem) -> Unit,
    onRetry: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        modifier = Modifier
            .fillMaxWidth()
            .height(260.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                },
                placeholder = {
                    Text(
                        text = stringResource(id = R.string.tenor_search_hint),
                        style = MaterialTheme.typography.bodySmall
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("tenor_search_input")
            )

            Spacer(modifier = Modifier.height(6.dp))

            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(GifQuickMoods) { (label, term) ->
                    FilterChip(
                        selected = selectedMood == label,
                        onClick = { onMoodSelected(label, term) },
                        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (tenorState) {
                    is TenorUiState.Idle,
                    is TenorUiState.Loading -> {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center),
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                    is TenorUiState.Error -> {
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = tenorState.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Tekrar Dene",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .clickable { onRetry() }
                                    .padding(8.dp)
                            )
                        }
                    }
                    is TenorUiState.Success -> {
                        if (tenorState.gifs.isEmpty()) {
                            Text(
                                text = "Eşleşen Tenor GIF bulunamadı.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(minSize = 112.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(tenorState.gifs, key = { it.id }) { gif ->
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(1f)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
                                            .border(
                                                1.dp,
                                                MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                                RoundedCornerShape(12.dp)
                                            )
                                            .clickable { onGifSelected(gif) }
                                    ) {
                                        AsyncImage(
                                            model = gif.previewUrl,
                                            imageLoader = gifImageLoader,
                                            contentDescription = gif.title,
                                            contentScale = ContentScale.Fit,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .aspectRatio(1f)
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
}
