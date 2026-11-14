package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.AppUpdateInfo

@Composable
fun GitHubUpdateNewsBanner(
    updateInfo: AppUpdateInfo?,
    onOpenDetails: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = updateInfo != null && updateInfo.isNewerAvailable,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
        modifier = modifier
    ) {
        if (updateInfo != null) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.96f),
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
                shadowElevation = 6.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("github_update_banner")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.NewReleases,
                        contentDescription = "Yeni GitHub güncellemesi",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Yeni Güncelleme: ${updateInfo.tagName}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = updateInfo.releaseNotes.lineSequence().firstOrNull { it.isNotBlank() }
                                ?: "GitHub üzerinde yeni sürüm yayınlandı.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Button(
                        onClick = onOpenDetails,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("github_update_banner_action")
                    ) {
                        Text("Detay", style = MaterialTheme.typography.labelMedium)
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("github_update_banner_dismiss")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Güncelleme haberini gizle",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun GitHubUpdateNewsDialog(
    updateInfo: AppUpdateInfo,
    onOpenGitHub: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val targetUrl = updateInfo.apkDownloadUrl ?: updateInfo.htmlUrl
    val publishedDate = updateInfo.publishedAt.take(10)

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Filled.SystemUpdate,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = if (updateInfo.isNewerAvailable) {
                    "Güncelleme Haberi • ${updateInfo.tagName}"
                } else {
                    "Son GitHub Sürümü • ${updateInfo.tagName}"
                },
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 280.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = updateInfo.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = buildString {
                                append("Mevcut: v${updateInfo.currentVersion}  •  GitHub: ${updateInfo.tagName}")
                                if (publishedDate.isNotBlank()) append("  •  $publishedDate")
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Depo: ${updateInfo.repoSlug}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }

                Text(
                    text = "Sürüm Notları",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = updateInfo.releaseNotes,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onOpenGitHub(targetUrl) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White
                ),
                modifier = Modifier.testTag("github_update_open_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.OpenInNew,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (updateInfo.apkDownloadUrl != null) "APK'yı İndir" else "GitHub'da Gör")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (updateInfo.apkDownloadUrl != null) {
                    OutlinedButton(onClick = { onOpenGitHub(updateInfo.htmlUrl) }) {
                        Text("Notlar")
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Kapat")
                }
            }
        }
    )
}

@Composable
fun GitHubRepoConfigDialog(
    initialRepo: String,
    isAdmin: Boolean,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var repoInput by remember(initialRepo) { mutableStateOf(initialRepo) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("GitHub Güncelleme Deposu") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = if (isAdmin) {
                        "GitHub deposunu girin (ör. kullanici/SocialWiew veya tam GitHub bağlantısı). Kaydettiğinizde tüm kullanıcılar bu depodan otomatik güncelleme haberi alır."
                    } else {
                        "Güncelleme kontrolü için GitHub deposunu girin (ör. kullanici/SocialWiew veya tam GitHub bağlantısı)."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = repoInput,
                    onValueChange = { repoInput = it },
                    label = { Text("GitHub Deposu (owner/repo)") },
                    placeholder = { Text("alparslan8903/SocialWiew") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("github_repo_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(repoInput) },
                modifier = Modifier.testTag("github_repo_save_button")
            ) {
                Text("Kaydet ve Kontrol Et")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("İptal")
            }
        }
    )
}
