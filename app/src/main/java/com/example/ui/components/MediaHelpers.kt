package com.example.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Looper
import android.util.Base64
import android.util.Log
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.platform.testTag
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import com.example.data.model.VideoPost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import kotlin.coroutines.resume

data class LocalVideoInfo(
    val fileName: String,
    val filePath: String,
    val localUri: Uri,
    val durationMs: Long,
    val thumbnailBase64: String,
    val sizeBytes: Long
)

object VideoMediaHelper {
    private const val TAG = "VideoMediaHelper"
    private const val VIDEO_DIR = "videos"
    private const val COMPRESS_THRESHOLD_BYTES = 5_500_000L
    const val MAX_UPLOAD_BYTES = 15_500_000L

    suspend fun copyPickedVideoToAppStorage(context: Context, sourceUri: Uri): LocalVideoInfo? =
        withContext(Dispatchers.IO) {
            try {
                val videosDir = File(context.filesDir, VIDEO_DIR).apply { mkdirs() }
                val fileName = "vid_${UUID.randomUUID().toString().replace("-", "").take(16)}.mp4"
                val destFile = File(videosDir, fileName)

                context.contentResolver.openInputStream(sourceUri)?.use { input ->
                    destFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                } ?: return@withContext null

                if (destFile.length() > COMPRESS_THRESHOLD_BYTES) {
                    val compressedFile = File(videosDir, "cmp_$fileName")
                    val compressed = compressVideoIfSmaller(context, destFile, compressedFile)
                    if (compressed && compressedFile.exists() && compressedFile.length() in 1L until destFile.length()) {
                        destFile.delete()
                        compressedFile.renameTo(destFile)
                    } else {
                        compressedFile.delete()
                    }
                }

                val retriever = MediaMetadataRetriever()
                var durationMs = 0L
                var thumbnailBase64 = ""
                try {
                    retriever.setDataSource(destFile.absolutePath)
                    durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                        ?.toLongOrNull() ?: 0L
                    val frame = retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    if (frame != null) {
                        val scaled = scaleBitmap(frame, maxDim = 360)
                        val baos = ByteArrayOutputStream()
                        scaled.compress(Bitmap.CompressFormat.JPEG, 65, baos)
                        thumbnailBase64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
                        if (scaled !== frame) scaled.recycle()
                        frame.recycle()
                    }
                } catch (_: Exception) {
                    // Metadata extraction best-effort
                } finally {
                    retriever.release()
                }

                LocalVideoInfo(
                    fileName = fileName,
                    filePath = destFile.absolutePath,
                    localUri = Uri.fromFile(destFile),
                    durationMs = durationMs,
                    thumbnailBase64 = thumbnailBase64,
                    sizeBytes = destFile.length()
                )
            } catch (_: Exception) {
                null
            }
        }

    suspend fun ensureUploadableLocalFile(context: Context, sourceFile: File): File? =
        withContext(Dispatchers.IO) {
            if (!sourceFile.exists() || sourceFile.length() <= 0L) return@withContext null
            if (sourceFile.length() <= COMPRESS_THRESHOLD_BYTES) return@withContext sourceFile
            val compressedFile = File(sourceFile.parentFile, "cmp_${sourceFile.name}")
            val compressed = compressVideoIfSmaller(context, sourceFile, compressedFile)
            if (compressed && compressedFile.exists() && compressedFile.length() in 1L..MAX_UPLOAD_BYTES) {
                compressedFile
            } else {
                compressedFile.delete()
                if (sourceFile.length() <= MAX_UPLOAD_BYTES) sourceFile else null
            }
        }

    @OptIn(UnstableApi::class)
    private suspend fun compressVideoIfSmaller(
        context: Context,
        inputFile: File,
        outputFile: File
    ): Boolean {
        return withTimeoutOrNull(45_000L) {
            withContext(Dispatchers.Main) {
                suspendCancellableCoroutine { cont ->
                    try {
                        val encoderFactory = DefaultEncoderFactory.Builder(context)
                            .setRequestedVideoEncoderSettings(
                                VideoEncoderSettings.Builder()
                                    .setBitrate(1_400_000)
                                    .build()
                            )
                            .build()

                        val transformer = Transformer.Builder(context)
                            .setLooper(Looper.getMainLooper())
                            .setVideoMimeType(MimeTypes.VIDEO_H264)
                            .setEncoderFactory(encoderFactory)
                            .addListener(object : Transformer.Listener {
                                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                                    if (cont.isActive) cont.resume(true)
                                }

                                override fun onError(
                                    composition: Composition,
                                    exportResult: ExportResult,
                                    exportException: ExportException
                                ) {
                                    Log.w(TAG, "Video compression skipped: ${exportException.message}")
                                    if (cont.isActive) cont.resume(false)
                                }
                            })
                            .build()

                        val editedItem = EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(inputFile)))
                            .setEffects(
                                Effects(
                                    emptyList(),
                                    listOf(Presentation.createForHeight(720))
                                )
                            )
                            .build()

                        transformer.start(editedItem, outputFile.absolutePath)
                        cont.invokeOnCancellation {
                            try {
                                transformer.cancel()
                            } catch (_: Exception) {
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Transformer init failed", e)
                        if (cont.isActive) cont.resume(false)
                    }
                }
            }
        } ?: false
    }

    fun getCloudCacheFile(context: Context, videoId: String): File {
        val cacheDir = File(context.cacheDir, VIDEO_DIR).apply { mkdirs() }
        return File(cacheDir, "$videoId.mp4")
    }

    fun resolvePlayableUri(context: Context, video: VideoPost): Uri? {
        if (video.localFileName.isNotBlank()) {
            val localFile = File(File(context.filesDir, VIDEO_DIR), video.localFileName)
            if (localFile.exists() && localFile.length() > 0L) {
                return Uri.fromFile(localFile)
            }
        }
        if (video.id.isNotBlank()) {
            val cachedFile = getCloudCacheFile(context, video.id)
            if (cachedFile.exists() && cachedFile.length() > 0L) {
                return Uri.fromFile(cachedFile)
            }
        }
        if (video.videoUrl.isNotBlank()) {
            return Uri.parse(video.videoUrl)
        }
        return null
    }

    fun decodeThumbnail(base64: String): Bitmap? {
        if (base64.isBlank()) return null
        return try {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (_: Exception) {
            null
        }
    }

    private fun scaleBitmap(src: Bitmap, maxDim: Int): Bitmap {
        val ratio = minOf(maxDim.toFloat() / src.width, maxDim.toFloat() / src.height)
        if (ratio >= 1f) return src
        val width = (src.width * ratio).toInt().coerceAtLeast(1)
        val height = (src.height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(src, width, height, true)
    }
}

@Composable
fun rememberGifImageLoader(): ImageLoader {
    val context = LocalContext.current
    return remember(context) {
        ImageLoader.Builder(context)
            .components {
                if (Build.VERSION.SDK_INT >= 28) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .crossfade(true)
            .build()
    }
}

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerSurface(
    videoUri: Uri?,
    thumbnailBase64: String,
    isActive: Boolean,
    isMuted: Boolean,
    modifier: Modifier = Modifier,
    cloudVideoId: String = "",
    cloudChunkCount: Int = 0,
    onFetchCloudVideo: (suspend (String) -> Uri?)? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var cloudResolvedUri by remember(videoUri, cloudVideoId) { mutableStateOf<Uri?>(null) }
    var isDownloadingCloud by remember(videoUri, cloudVideoId, cloudChunkCount) {
        mutableStateOf(videoUri == null && cloudChunkCount > 0 && cloudVideoId.isNotBlank())
    }
    var cloudDownloadFailed by remember(videoUri, cloudVideoId) { mutableStateOf(false) }

    LaunchedEffect(videoUri, cloudVideoId, cloudChunkCount) {
        if (videoUri == null && cloudChunkCount > 0 && cloudVideoId.isNotBlank() && onFetchCloudVideo != null) {
            isDownloadingCloud = true
            cloudDownloadFailed = false
            val resolved = onFetchCloudVideo(cloudVideoId)
            cloudResolvedUri = resolved
            cloudDownloadFailed = resolved == null
            isDownloadingCloud = false
        } else {
            isDownloadingCloud = false
        }
    }

    val effectiveUri = videoUri ?: cloudResolvedUri

    var userPaused by remember(effectiveUri) { mutableStateOf(false) }
    var showFeedbackIcon by remember { mutableStateOf(false) }
    var isBuffering by remember(effectiveUri) { mutableStateOf(effectiveUri != null) }
    var hasPlaybackError by remember(effectiveUri) { mutableStateOf(false) }

    val thumbnailBitmap = remember(thumbnailBase64) {
        VideoMediaHelper.decodeThumbnail(thumbnailBase64)
    }

    val exoPlayer = remember(effectiveUri) {
        if (effectiveUri == null) null
        else ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(effectiveUri))
            repeatMode = Player.REPEAT_MODE_ONE
            prepare()
        }
    }

    LaunchedEffect(exoPlayer, isActive, userPaused, isMuted) {
        exoPlayer?.let { player ->
            player.volume = if (isMuted) 0f else 1f
            player.playWhenReady = isActive && !userPaused
        }
    }

    LaunchedEffect(showFeedbackIcon) {
        if (showFeedbackIcon) {
            delay(650L)
            showFeedbackIcon = false
        }
    }

    DisposableEffect(exoPlayer, lifecycleOwner) {
        if (exoPlayer == null) {
            onDispose { }
        } else {
            val playerListener = object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    isBuffering = playbackState == Player.STATE_BUFFERING
                }

                override fun onPlayerError(error: PlaybackException) {
                    if (cloudVideoId.isNotBlank() && cloudChunkCount > 0) {
                        runCatching { VideoMediaHelper.getCloudCacheFile(context, cloudVideoId).delete() }
                    }
                    hasPlaybackError = true
                    isBuffering = false
                }
            }
            exoPlayer.addListener(playerListener)

            val lifecycleObserver = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_PAUSE -> exoPlayer.pause()
                    Lifecycle.Event.ON_RESUME -> {
                        if (isActive && !userPaused) exoPlayer.play()
                    }
                    else -> Unit
                }
            }
            lifecycleOwner.lifecycle.addObserver(lifecycleObserver)

            onDispose {
                lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
                exoPlayer.removeListener(playerListener)
                exoPlayer.release()
            }
        }
    }

    Box(
        modifier = modifier
            .background(Color(0xFF06070B))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                if (exoPlayer != null) {
                    userPaused = !userPaused
                    showFeedbackIcon = true
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (thumbnailBitmap != null) {
            Image(
                bitmap = thumbnailBitmap.asImageBitmap(),
                contentDescription = "Video önizleme karesi",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        if (exoPlayer != null && !hasPlaybackError) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        player = exoPlayer
                    }
                },
                update = { view ->
                    if (view.player !== exoPlayer) {
                        view.player = exoPlayer
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        if (isDownloadingCloud) {
            Surface(
                color = Color.Black.copy(alpha = 0.6f),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.padding(24.dp)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(32.dp)
                    )
                    Text(
                        text = "Video buluttan hazırlanıyor...",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        } else if (effectiveUri == null || hasPlaybackError || cloudDownloadFailed) {
            Surface(
                color = Color.Black.copy(alpha = 0.65f),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.padding(24.dp)
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Filled.VideocamOff,
                            contentDescription = "Video kaynağı bulunamadı",
                            tint = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.size(28.dp)
                        )
                        Text(
                            text = if (effectiveUri == null && cloudChunkCount == 0) {
                                "Bu eski video henüz buluta yüklenmemiş. İlk yüklendiği cihazdan uygulama açıldığında otomatik eşitlenir."
                            } else {
                                "Video oynatılamadı"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }
            }
        } else if (isBuffering) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 3.dp,
                modifier = Modifier.size(40.dp)
            )
        }

        AnimatedVisibility(
            visible = showFeedbackIcon || userPaused,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.55f),
                modifier = Modifier.size(68.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (userPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                        contentDescription = if (userPaused) "Oynat" else "Duraklat",
                        tint = Color.White,
                        modifier = Modifier.size(38.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun CreatorAvatar(
    name: String,
    avatarUrl: String,
    size: Dp = 42.dp,
    modifier: Modifier = Modifier
) {
    val initial = name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "V"
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.colorScheme.secondary
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initial,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        if (avatarUrl.isNotBlank()) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = "$name profil fotoğrafı",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
            )
        }
    }
}

val VerifiedBlue = Color(0xFF1D9BF0)

@Composable
fun VerifiedTickIcon(
    size: Dp = 16.dp,
    modifier: Modifier = Modifier
) {
    Icon(
        imageVector = Icons.Filled.Verified,
        contentDescription = "Doğrulanmış Hesap",
        tint = VerifiedBlue,
        modifier = modifier
            .size(size)
            .testTag("verified_tick_icon")
    )
}

@Composable
fun VerifiedAdminBadge(
    label: String = "Admin • Doğrulanmış",
    modifier: Modifier = Modifier
) {
    Surface(
        color = VerifiedBlue.copy(alpha = 0.14f),
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(1.dp, VerifiedBlue.copy(alpha = 0.45f)),
        modifier = modifier.testTag("verified_admin_badge")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Verified,
                contentDescription = "Doğrulanmış Admin",
                tint = VerifiedBlue,
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = VerifiedBlue
            )
        }
    }
}

