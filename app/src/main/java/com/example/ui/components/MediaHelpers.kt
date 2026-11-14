package com.example.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.Base64
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import com.example.data.model.VideoPost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

data class LocalVideoInfo(
    val fileName: String,
    val localUri: Uri,
    val durationMs: Long,
    val thumbnailBase64: String
)

object VideoMediaHelper {
    private const val VIDEO_DIR = "videos"

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
                    localUri = Uri.fromFile(destFile),
                    durationMs = durationMs,
                    thumbnailBase64 = thumbnailBase64
                )
            } catch (_: Exception) {
                null
            }
        }

    fun resolvePlayableUri(context: Context, video: VideoPost): Uri? {
        if (video.localFileName.isNotBlank()) {
            val localFile = File(File(context.filesDir, VIDEO_DIR), video.localFileName)
            if (localFile.exists() && localFile.length() > 0L) {
                return Uri.fromFile(localFile)
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
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var userPaused by remember(videoUri) { mutableStateOf(false) }
    var showFeedbackIcon by remember { mutableStateOf(false) }
    var isBuffering by remember(videoUri) { mutableStateOf(videoUri != null) }
    var hasPlaybackError by remember(videoUri) { mutableStateOf(false) }

    val thumbnailBitmap = remember(thumbnailBase64) {
        VideoMediaHelper.decodeThumbnail(thumbnailBase64)
    }

    val exoPlayer = remember(videoUri) {
        if (videoUri == null) null
        else ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(videoUri))
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

        if (videoUri == null || hasPlaybackError) {
            Surface(
                color = Color.Black.copy(alpha = 0.65f),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.padding(24.dp)
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.foundation.layout.Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Filled.VideocamOff,
                            contentDescription = "Video kaynağı bulunamadı",
                            tint = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.size(28.dp)
                        )
                        Text(
                            text = if (videoUri == null) "Video başka bir cihazın yerel dosyası" else "Video oynatılamadı",
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
