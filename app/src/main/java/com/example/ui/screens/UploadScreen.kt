package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.components.LocalVideoInfo
import com.example.ui.components.VideoMediaHelper
import com.example.ui.components.VideoPlayerSurface
import kotlinx.coroutines.launch

private val SuggestedTags = listOf("vlog", "müzik", "komedi", "dans", "oyun", "spor", "seyahat")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UploadScreen(
    isPublishing: Boolean,
    onPublishVideo: (
        caption: String,
        videoUrl: String,
        localFileName: String?,
        thumbnailData: String?,
        durationMs: Long,
        tags: List<String>,
        onSuccess: () -> Unit
    ) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var pickedVideo by remember { mutableStateOf<LocalVideoInfo?>(null) }
    var isProcessingPick by remember { mutableStateOf(false) }
    var videoUrlInput by remember { mutableStateOf("") }
    var caption by remember { mutableStateOf("") }
    var selectedTags by remember { mutableStateOf(setOf("vlog")) }
    var errorNote by remember { mutableStateOf<String?>(null) }

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            errorNote = null
            isProcessingPick = true
            scope.launch {
                val info = VideoMediaHelper.copyPickedVideoToAppStorage(context, uri)
                isProcessingPick = false
                if (info != null) {
                    pickedVideo = info
                } else {
                    errorNote = "Seçilen video dosyası okunamadı."
                }
            }
        }
    }

    val previewUri = remember(pickedVideo, videoUrlInput) {
        when {
            pickedVideo != null -> pickedVideo?.localUri
            videoUrlInput.trim().startsWith("http", ignoreCase = true) -> Uri.parse(videoUrlInput.trim())
            else -> null
        }
    }

    val canPublish = !isPublishing && !isProcessingPick &&
        (pickedVideo != null || videoUrlInput.trim().startsWith("http", ignoreCase = true))

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        val isExpandedTablet = maxWidth >= 840.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = if (isExpandedTablet) 32.dp else 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = if (isExpandedTablet) 1040.dp else 580.dp)
            ) {
                Text(
                    text = stringResource(id = R.string.upload_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Cihazından bir video seç veya doğrudan MP4/HLS video bağlantısı paylaş.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 18.dp)
                )

                val previewAndPickerPane: @Composable (Modifier) -> Unit = { paneModifier ->
                    Column(modifier = paneModifier) {
                        Surface(
                            shape = RoundedCornerShape(22.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(if (isExpandedTablet) 4f / 3f else 16f / 10f)
                                .clip(RoundedCornerShape(22.dp))
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                if (isProcessingPick) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text("Video hazırlanıyor...", style = MaterialTheme.typography.bodySmall)
                                    }
                                } else if (previewUri != null) {
                                    VideoPlayerSurface(
                                        videoUri = previewUri,
                                        thumbnailBase64 = pickedVideo?.thumbnailBase64 ?: "",
                                        isActive = true,
                                        isMuted = true,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.padding(20.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.VideoLibrary,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(40.dp)
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "Video önizlemesi burada görünecek",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        OutlinedButton(
                            onClick = {
                                videoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                )
                            },
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("pick_video_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.VideoLibrary,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (pickedVideo != null) "Başka Video Seç"
                                else stringResource(id = R.string.pick_video_gallery),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }

                val metadataAndPublishPane: @Composable (Modifier) -> Unit = { paneModifier ->
                    Column(modifier = paneModifier) {
                        OutlinedTextField(
                            value = videoUrlInput,
                            onValueChange = { videoUrlInput = it },
                            label = { Text("Veya Video URL'si (https://...mp4)") },
                            leadingIcon = {
                                Icon(imageVector = Icons.Filled.Link, contentDescription = null)
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.secondary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("video_url_input")
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = caption,
                            onValueChange = { if (it.length <= 300) caption = it },
                            label = { Text("Açıklama") },
                            placeholder = { Text("Bu videonun hikayesini yaz...") },
                            minLines = 3,
                            maxLines = 4,
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("video_caption_input")
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = "Etiketler (en fazla 5)",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            SuggestedTags.forEach { tag ->
                                val selected = selectedTags.contains(tag)
                                FilterChip(
                                    selected = selected,
                                    onClick = {
                                        selectedTags = if (selected) {
                                            selectedTags - tag
                                        } else if (selectedTags.size < 5) {
                                            selectedTags + tag
                                        } else {
                                            selectedTags
                                        }
                                    },
                                    label = { Text("#$tag") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                )
                            }
                        }

                        if (errorNote != null) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = errorNote ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Button(
                            onClick = {
                                errorNote = null
                                onPublishVideo(
                                    caption,
                                    videoUrlInput.trim(),
                                    pickedVideo?.fileName,
                                    pickedVideo?.thumbnailBase64,
                                    pickedVideo?.durationMs ?: 0L,
                                    selectedTags.toList()
                                ) {
                                    pickedVideo = null
                                    videoUrlInput = ""
                                    caption = ""
                                    selectedTags = setOf("vlog")
                                }
                            },
                            enabled = canPublish,
                            shape = RoundedCornerShape(18.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = Color.White
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .testTag("publish_video_button")
                        ) {
                            if (isPublishing) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    strokeWidth = 2.5.dp,
                                    modifier = Modifier.size(22.dp)
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Filled.CloudUpload,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(id = R.string.publish_video),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                if (isExpandedTablet) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        previewAndPickerPane(Modifier.weight(1f))
                        metadataAndPublishPane(Modifier.weight(1f))
                    }
                } else {
                    previewAndPickerPane(Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(14.dp))
                    metadataAndPublishPane(Modifier.fillMaxWidth())
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
