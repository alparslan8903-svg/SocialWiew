package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.UserPublic
import com.example.data.model.VideoPost
import com.example.data.repository.SocialRepository
import com.example.ui.components.CreatorAvatar
import com.example.ui.components.VerifiedTickIcon
import com.example.ui.components.VideoMediaHelper
import com.example.ui.viewmodel.UiState
import com.google.firebase.Timestamp

private enum class SearchScope(val labelTr: String) {
    ALL("Tümü"),
    CREATORS("Üreticiler"),
    TRENDING("Trend Videolar")
}

private val FallbackTrendingTags = listOf("vlog", "müzik", "komedi", "dans", "oyun", "spor", "seyahat")

@Composable
fun SearchScreen(
    creatorsState: UiState<List<UserPublic>>,
    videosState: UiState<List<VideoPost>>,
    currentUserId: String,
    verifiedUserIds: Set<String> = emptySet(),
    followingIds: Set<String>,
    onToggleFollow: (String) -> Unit,
    onOpenCreatorProfile: (String) -> Unit,
    onOpenVideoPlayer: (VideoPost) -> Unit
) {
    val focusManager = LocalFocusManager.current
    var query by remember { mutableStateOf("") }
    var selectedTag by remember { mutableStateOf<String?>(null) }
    var selectedScope by remember { mutableStateOf(SearchScope.ALL) }
    val recentSearches = remember { mutableStateListOf<String>() }

    val allCreators = (creatorsState as? UiState.Success)?.data.orEmpty()
    val allVideos = (videosState as? UiState.Success)?.data.orEmpty()

    val availableTags = remember(allVideos) {
        val fromVideos = allVideos
            .flatMap { it.tags }
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .map { it.key }
        (fromVideos + FallbackTrendingTags).distinct().take(10)
    }

    val normalizedQuery = query.trim().removePrefix("#").removePrefix("@")

    // Prioritize new creators not yet followed when browsing without an active query
    val filteredCreators = remember(allCreators, normalizedQuery, followingIds, currentUserId) {
        allCreators
            .filter { creator ->
                if (normalizedQuery.isBlank()) {
                    creator.userId != currentUserId
                } else {
                    creator.displayName.contains(normalizedQuery, ignoreCase = true) ||
                        creator.username.contains(normalizedQuery, ignoreCase = true) ||
                        creator.bio.contains(normalizedQuery, ignoreCase = true)
                }
            }
            .sortedWith(
                compareBy<UserPublic> { followingIds.contains(it.userId) }
                    .thenByDescending { it.videosCount }
                    .thenByDescending { it.followersCount }
            )
    }

    // Rank trending videos by engagement score (likes + weighted comments), then recency
    val trendingVideos = remember(allVideos, normalizedQuery, selectedTag) {
        allVideos
            .filter { video ->
                val matchesTag = selectedTag == null ||
                    video.tags.any { it.equals(selectedTag, ignoreCase = true) }
                val matchesQuery = normalizedQuery.isBlank() ||
                    video.caption.contains(normalizedQuery, ignoreCase = true) ||
                    video.authorName.contains(normalizedQuery, ignoreCase = true) ||
                    video.authorUsername.contains(normalizedQuery, ignoreCase = true) ||
                    video.tags.any { it.contains(normalizedQuery, ignoreCase = true) }
                matchesTag && matchesQuery
            }
            .sortedWith(
                compareByDescending<VideoPost> { it.likesCount * 2L + it.commentsCount * 3L }
                    .thenByDescending { it.createdAt ?: Timestamp(0, 0) }
            )
    }

    val recordSearchHistory: (String) -> Unit = { term ->
        val clean = term.trim()
        if (clean.length >= 2) {
            recentSearches.remove(clean)
            recentSearches.add(0, clean)
            if (recentSearches.size > 6) {
                recentSearches.removeAt(recentSearches.lastIndex)
            }
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        val isExpandedTablet = maxWidth >= 840.dp
        val isMediumOrLarger = maxWidth >= 600.dp

        Column(modifier = Modifier.fillMaxSize()) {
            // Search Header
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = if (isMediumOrLarger) 24.dp else 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.TrendingUp,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(id = R.string.search_title),
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }

                    if (!isExpandedTablet) {
                        Text(
                            text = "${trendingVideos.size} video",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(
                                    onClick = { query = "" },
                                    modifier = Modifier.testTag("search_clear_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = "Aramayı temizle"
                                    )
                                }
                            }
                        },
                        placeholder = { Text(stringResource(id = R.string.search_hint)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onSearch = {
                                recordSearchHistory(query)
                                focusManager.clearFocus()
                            }
                        ),
                        shape = RoundedCornerShape(18.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .widthIn(max = 640.dp)
                            .testTag("search_screen_input")
                    )

                    if (isExpandedTablet) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SearchScope.entries.forEach { scope ->
                                FilterChip(
                                    selected = selectedScope == scope,
                                    onClick = { selectedScope = scope },
                                    label = { Text(scope.labelTr) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    ),
                                    modifier = Modifier.testTag("search_scope_${scope.name.lowercase()}")
                                )
                            }
                        }
                    }
                }

                if (!isExpandedTablet) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(SearchScope.entries) { scope ->
                            FilterChip(
                                selected = selectedScope == scope,
                                onClick = { selectedScope = scope },
                                label = { Text(scope.labelTr) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                modifier = Modifier.testTag("search_scope_${scope.name.lowercase()}")
                            )
                        }
                    }
                }

                // Trending Hashtags Filter Strip
                if (selectedScope != SearchScope.CREATORS) {
                    Spacer(modifier = Modifier.height(6.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            FilterChip(
                                selected = selectedTag == null,
                                onClick = { selectedTag = null },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Filled.LocalFireDepartment,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                label = { Text("Tüm Trendler") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                                ),
                                modifier = Modifier.testTag("search_tag_all")
                            )
                        }
                        items(availableTags) { tag ->
                            FilterChip(
                                selected = selectedTag.equals(tag, ignoreCase = true),
                                onClick = {
                                    selectedTag = if (selectedTag.equals(tag, ignoreCase = true)) null else tag
                                },
                                label = { Text("#$tag") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                                ),
                                modifier = Modifier.testTag("search_tag_$tag")
                            )
                        }
                    }
                }

                // Recent Searches Strip
                if (recentSearches.isNotEmpty() && query.isBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        item {
                            Icon(
                                imageVector = Icons.Filled.History,
                                contentDescription = "Son aramalar",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        items(recentSearches) { item ->
                            InputChip(
                                selected = false,
                                onClick = { query = item },
                                label = { Text(item, style = MaterialTheme.typography.labelMedium) },
                                trailingIcon = {
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = "Geçmişten kaldır",
                                        modifier = Modifier
                                            .size(14.dp)
                                            .clickable { recentSearches.remove(item) }
                                    )
                                }
                            )
                        }
                        item {
                            TextButton(onClick = { recentSearches.clear() }) {
                                Text("Temizle", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))

            if (creatorsState is UiState.Loading && videosState is UiState.Loading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else if (isExpandedTablet && selectedScope == SearchScope.ALL) {
                // Expanded Tablet Two-Pane Discovery Layout
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    // Left Pane: New & Suggested Creators
                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                        shape = RoundedCornerShape(24.dp),
                        tonalElevation = 1.dp,
                        modifier = Modifier
                            .width(360.dp)
                            .fillMaxHeight()
                    ) {
                        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                            SectionTitleRow(
                                title = stringResource(id = R.string.search_creators_section),
                                count = filteredCreators.size
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            if (filteredCreators.isEmpty()) {
                                EmptySearchCard(
                                    message = "Eşleşen içerik üreticisi bulunamadı.",
                                    onReset = { query = "" }
                                )
                            } else {
                                LazyColumn(
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    items(filteredCreators, key = { it.userId }) { creator ->
                                        CreatorSearchListRow(
                                            creator = creator,
                                            isCurrentUser = creator.userId == currentUserId,
                                            isFollowing = followingIds.contains(creator.userId),
                                            onToggleFollow = { onToggleFollow(creator.userId) },
                                            onClick = {
                                                recordSearchHistory(query)
                                                onOpenCreatorProfile(creator.userId)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Right Pane: Trending Videos Adaptive Grid
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        SectionTitleRow(
                            title = stringResource(id = R.string.search_trending_section),
                            count = trendingVideos.size
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        if (trendingVideos.isEmpty()) {
                            EmptySearchCard(
                                message = "Bu arama veya etiket için trend video bulunamadı.",
                                onReset = {
                                    query = ""
                                    selectedTag = null
                                }
                            )
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(minSize = 185.dp),
                                contentPadding = PaddingValues(bottom = 24.dp),
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .testTag("search_trending_grid")
                            ) {
                                itemsIndexed(trendingVideos, key = { _, v -> v.id }) { index, video ->
                                    TrendingVideoGridCard(
                                        video = video,
                                        isAuthorVerified = video.authorVerified || verifiedUserIds.contains(video.userId),
                                        rank = if (normalizedQuery.isBlank() && selectedTag == null && index < 3) index + 1 else null,
                                        onClick = {
                                            recordSearchHistory(query)
                                            onOpenVideoPlayer(video)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // Compact & Medium (and scoped tablet view)
                val gridMinSize = if (isMediumOrLarger) 185.dp else 160.dp
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = gridMinSize),
                    contentPadding = PaddingValues(
                        start = if (isMediumOrLarger) 24.dp else 16.dp,
                        end = if (isMediumOrLarger) 24.dp else 16.dp,
                        top = 12.dp,
                        bottom = 24.dp
                    ),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("search_results_grid")
                ) {
                    if (selectedScope != SearchScope.TRENDING) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            SectionTitleRow(
                                title = stringResource(id = R.string.search_creators_section),
                                count = filteredCreators.size
                            )
                        }

                        if (filteredCreators.isEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                EmptySearchCard(
                                    message = "Aramanızla eşleşen yeni içerik üreticisi bulunamadı.",
                                    onReset = { query = "" }
                                )
                            }
                        } else if (selectedScope == SearchScope.CREATORS) {
                            itemsIndexed(
                                items = filteredCreators,
                                key = { _, c -> "creator_${c.userId}" }
                            ) { _, creator ->
                                CreatorSearchListRow(
                                    creator = creator,
                                    isCurrentUser = creator.userId == currentUserId,
                                    isFollowing = followingIds.contains(creator.userId),
                                    onToggleFollow = { onToggleFollow(creator.userId) },
                                    onClick = {
                                        recordSearchHistory(query)
                                        onOpenCreatorProfile(creator.userId)
                                    }
                                )
                            }
                        } else {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    items(filteredCreators, key = { it.userId }) { creator ->
                                        CreatorSearchCardCompact(
                                            creator = creator,
                                            isCurrentUser = creator.userId == currentUserId,
                                            isFollowing = followingIds.contains(creator.userId),
                                            onToggleFollow = { onToggleFollow(creator.userId) },
                                            onClick = {
                                                recordSearchHistory(query)
                                                onOpenCreatorProfile(creator.userId)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (selectedScope != SearchScope.CREATORS) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            SectionTitleRow(
                                title = stringResource(id = R.string.search_trending_section),
                                count = trendingVideos.size,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        }

                        if (trendingVideos.isEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                EmptySearchCard(
                                    message = "Aramanızla eşleşen trend video bulunamadı.",
                                    onReset = {
                                        query = ""
                                        selectedTag = null
                                    }
                                )
                            }
                        } else {
                            itemsIndexed(trendingVideos, key = { _, v -> v.id }) { index, video ->
                                TrendingVideoGridCard(
                                    video = video,
                                    isAuthorVerified = video.authorVerified || verifiedUserIds.contains(video.userId),
                                    rank = if (normalizedQuery.isBlank() && selectedTag == null && index < 3) index + 1 else null,
                                    onClick = {
                                        recordSearchHistory(query)
                                        onOpenVideoPlayer(video)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitleRow(
    title: String,
    count: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(10.dp)
        ) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun CreatorSearchListRow(
    creator: UserPublic,
    isCurrentUser: Boolean,
    isFollowing: Boolean,
    onToggleFollow: () -> Unit,
    onClick: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .testTag("search_creator_row_${creator.userId}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CreatorAvatar(
                name = creator.displayName,
                avatarUrl = creator.avatarUrl,
                size = 50.dp
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                val isCreatorVerified = creator.isVerified || SocialRepository.isVerifiedUsername(creator.username)
                val isCreatorAdmin = SocialRepository.isAdminUsername(creator.username)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = creator.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (isCreatorVerified) {
                        Spacer(modifier = Modifier.width(5.dp))
                        VerifiedTickIcon(size = 16.dp)
                    }
                }
                Text(
                    text = buildString {
                        append("@${creator.username}")
                        if (isCreatorAdmin) append(" • Admin")
                        append(" • ${creator.followersCount} takipçi")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (creator.bio.isNotBlank()) {
                    Text(
                        text = creator.bio,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            if (!isCurrentUser) {
                FilledTonalButton(
                    onClick = onToggleFollow,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = if (isFollowing) MaterialTheme.colorScheme.surface
                        else MaterialTheme.colorScheme.primary,
                        contentColor = if (isFollowing) MaterialTheme.colorScheme.onSurface
                        else Color.White
                    )
                ) {
                    Text(
                        text = if (isFollowing) stringResource(id = R.string.following)
                        else stringResource(id = R.string.follow),
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun CreatorSearchCardCompact(
    creator: UserPublic,
    isCurrentUser: Boolean,
    isFollowing: Boolean,
    onToggleFollow: () -> Unit,
    onClick: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 2.dp,
        modifier = Modifier
            .width(166.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .testTag("search_creator_card_${creator.userId}")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CreatorAvatar(
                name = creator.displayName,
                avatarUrl = creator.avatarUrl,
                size = 54.dp
            )
            Spacer(modifier = Modifier.height(8.dp))
            val isCreatorVerified = creator.isVerified || SocialRepository.isVerifiedUsername(creator.username)
            val isCreatorAdmin = SocialRepository.isAdminUsername(creator.username)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = creator.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (isCreatorVerified) {
                    Spacer(modifier = Modifier.width(4.dp))
                    VerifiedTickIcon(size = 16.dp)
                }
            }
            Text(
                text = if (isCreatorAdmin) "@${creator.username} • Admin" else "@${creator.username}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${creator.followersCount} takipçi • ${creator.videosCount} video",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(10.dp))
            if (!isCurrentUser) {
                FilledTonalButton(
                    onClick = onToggleFollow,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = if (isFollowing) MaterialTheme.colorScheme.surfaceVariant
                        else MaterialTheme.colorScheme.primary,
                        contentColor = if (isFollowing) MaterialTheme.colorScheme.onSurface
                        else Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (isFollowing) stringResource(id = R.string.following)
                        else stringResource(id = R.string.follow),
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun TrendingVideoGridCard(
    video: VideoPost,
    rank: Int?,
    onClick: () -> Unit,
    isAuthorVerified: Boolean = video.authorVerified
) {
    val thumbBitmap = remember(video.thumbnailData) {
        VideoMediaHelper.decodeThumbnail(video.thumbnailData)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.74f)
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surfaceVariant,
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                    )
                )
            )
            .clickable(onClick = onClick)
            .testTag("trending_video_card_${video.id}")
    ) {
        if (thumbBitmap != null) {
            Image(
                bitmap = thumbBitmap.asImageBitmap(),
                contentDescription = video.caption.ifBlank { "Trend video" },
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.72f),
                modifier = Modifier
                    .size(44.dp)
                    .align(Alignment.Center)
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Black.copy(alpha = 0.25f), Color.Transparent, Color.Black.copy(alpha = 0.86f))
                    )
                )
        )

        if (rank != null) {
            Surface(
                color = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(10.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.LocalFireDepartment,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "#$rank Trend",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(10.dp)
        ) {
            if (video.caption.isNotBlank()) {
                Text(
                    text = video.caption,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "@${video.authorUsername}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.85f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (isAuthorVerified || SocialRepository.isVerifiedUsername(video.authorUsername)) {
                        Spacer(modifier = Modifier.width(4.dp))
                        VerifiedTickIcon(size = 13.dp)
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Favorite,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = video.likesCount.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.ChatBubble,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = video.commentsCount.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptySearchCard(
    message: String,
    onReset: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = onReset) {
                Text("Filtreleri Sıfırla")
            }
        }
    }
}
