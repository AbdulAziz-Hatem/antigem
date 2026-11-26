package ai.antigem.ui.drawer

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import ai.antigem.domain.model.ArtifactSnapshot
import ai.antigem.theme.ClaudeTerracotta
import ai.antigem.theme.GeminiBlue
import ai.antigem.ui.components.ArtifactDownloadHelper
import ai.antigem.ui.components.FullScreenImageDialog
import ai.antigem.ui.components.LocalFileLinkHandler
import ai.antigem.ui.components.LocalSnackbarHostState
import ai.antigem.ui.components.accessibleMutedColor
import ai.antigem.ui.components.ensureAccessibleContrast
import ai.antigem.ui.components.formatCountForSpeech
import ai.antigem.ui.components.minTouchTarget
import kotlinx.coroutines.launch
import java.io.File
import ai.antigem.i18n.L

@Composable
fun ArtifactsDrawerContent(
    artifacts: List<ArtifactSnapshot>,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = LocalSnackbarHostState.current
    val fileLinkHandler = LocalFileLinkHandler.current

    var selectedImageForPreview by remember { mutableStateOf<ArtifactSnapshot?>(null) }
    val surfaceBg = MaterialTheme.colorScheme.surface
    val mutedText = accessibleMutedColor(alpha = 0.72f, background = surfaceBg)
    val mutedIcon = accessibleMutedColor(alpha = 0.72f, background = surfaceBg, minRatio = 3.0f)
    val badgeBg = ClaudeTerracotta.copy(alpha = 0.18f)
    val accessibleBadgeText = ensureAccessibleContrast(ClaudeTerracotta, badgeBg, 4.5f, surfaceBg)
    val accessibleHeaderIcon = ensureAccessibleContrast(ClaudeTerracotta, ClaudeTerracotta.copy(alpha = 0.15f), 3.0f, surfaceBg)

    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(surfaceBg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .semantics {
                paneTitle = L.drawer.artifactsDrawer.artifacts
                isTraversalGroup = true
            }
    ) {
        // Top Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(ClaudeTerracotta.copy(alpha = 0.15f))
                    .clearAndSetSemantics {},
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Layers,
                    contentDescription = null,
                    tint = accessibleHeaderIcon,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = L.drawer.artifactsDrawer.artifacts,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.semantics { heading() }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = badgeBg,
                        border = BorderStroke(0.8.dp, ClaudeTerracotta.copy(alpha = 0.35f))
                    ) {
                        Text(
                            text = "${artifacts.size}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = accessibleBadgeText,
                            modifier = Modifier
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                                .semantics { contentDescription = formatCountForSpeech(artifacts.size) }
                        )
                    }
                }
                Text(
                    text = L.drawer.artifactsDrawer.generatedFilesPlansMedia,
                    fontSize = 11.5.sp,
                    color = mutedText
                )
            }

            IconButton(
                onClick = onClose,
                modifier = Modifier.minTouchTarget(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = L.common.close,
                    tint = mutedIcon,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        HorizontalDivider(
            thickness = 0.8.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        )

        // Artifacts List or Empty State
        if (artifacts.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.FolderOpen,
                        contentDescription = null,
                        tint = mutedIcon,
                        modifier = Modifier
                            .size(48.dp)
                            .clearAndSetSemantics {}
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = L.drawer.artifactsDrawer.noArtifactsGeneratedYet,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = mutedText
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = L.drawer.artifactsDrawer.plansWalkthroughsAndGeneratedImagesWill,
                        fontSize = 12.sp,
                        color = mutedText,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(artifacts, key = { it.absoluteUri.ifBlank { it.name } }) { artifact ->
                    ArtifactCardItem(
                        artifact = artifact,
                        onOpen = {
                            val uri = artifact.absoluteUri.ifBlank { artifact.name }
                            if (isImageArtifact(artifact)) {
                                selectedImageForPreview = artifact
                            } else {
                                fileLinkHandler.onOpenFile(uri)
                            }
                        },
                        onDownload = {
                            ArtifactDownloadHelper.downloadArtifact(
                                context = context,
                                artifact = artifact,
                                coroutineScope = coroutineScope,
                                snackbarHostState = snackbarHostState
                            )
                        }
                    )
                }
            }
        }
    }

    // Full-screen Image Viewer
    selectedImageForPreview?.let { imgArtifact ->
        FullScreenImageDialog(
            imageUrl = imgArtifact.absoluteUri.ifBlank { imgArtifact.name },
            title = imgArtifact.name,
            onDismiss = { selectedImageForPreview = null }
        )
    }
}

@Composable
private fun ArtifactCardItem(
    artifact: ArtifactSnapshot,
    onOpen: () -> Unit,
    onDownload: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isImage = isImageArtifact(artifact)
    val isPlan = artifact.name.contains("plan", ignoreCase = true) || artifact.absoluteUri.contains("plan", ignoreCase = true)
    val isWalkthrough = artifact.name.contains("walkthrough", ignoreCase = true) || artifact.absoluteUri.contains("walkthrough", ignoreCase = true)

    val displayTitle = if (isPlan) {
        L.drawer.artifactsDrawer.implementationPlan
    } else if (isWalkthrough) {
        L.drawer.artifactsDrawer.walkthrough
    } else if (artifact.name.isNotBlank()) {
        val clean = artifact.name.substringAfterLast("/").replace("_", " ").replace("-", " ")
        clean.split(" ").filter { it.isNotBlank() }.joinToString(" ") { word -> word.replaceFirstChar { it.uppercase() } }
    } else {
        artifact.absoluteUri.substringAfterLast("/")
    }

    val subtitle = if (artifact.name.isNotBlank()) {
        artifact.name.substringAfterLast("/")
    } else {
        artifact.absoluteUri.substringAfterLast("/")
    }

    val surfaceBg = MaterialTheme.colorScheme.surface
    val cardBg = MaterialTheme.colorScheme.surfaceContainerHigh
    val cardMuted = accessibleMutedColor(alpha = 0.72f, background = cardBg, surfaceFallback = surfaceBg)
    val summaryBg = MaterialTheme.colorScheme.surfaceContainerLow
    val summaryText = ensureAccessibleContrast(
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
        summaryBg,
        4.5f,
        surfaceBg
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .minTouchTarget(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onOpen)
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction(L.drawer.artifactsDrawer.downloadToDownloadsAntigem) {
                        onDownload()
                        true
                    }
                )
            },
        shape = RoundedCornerShape(12.dp),
        color = cardBg,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Leading Thumbnail / Icon
                if (isImage) {
                    val rawUri = artifact.absoluteUri.ifBlank { artifact.name }
                    val resolvedModel = if (rawUri.startsWith("file://")) File(rawUri.removePrefix("file://")) else rawUri
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerLowest),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(resolvedModel)
                                .crossfade(true)
                                .build(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                } else {
                    val (icon, bgTint, rawIconTint) = when {
                        isPlan -> Triple(Icons.Outlined.AutoAwesome, ClaudeTerracotta.copy(alpha = 0.16f), ClaudeTerracotta)
                        isWalkthrough -> Triple(Icons.Outlined.Description, GeminiBlue.copy(alpha = 0.16f), GeminiBlue)
                        else -> Triple(Icons.AutoMirrored.Outlined.InsertDriveFile, ai.antigem.theme.AppColors.accentTeal.copy(alpha = 0.16f), ai.antigem.theme.AppColors.accentTeal)
                    }
                    val iconTint = ensureAccessibleContrast(rawIconTint, bgTint, 3.0f, cardBg)
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(bgTint)
                            .clearAndSetSemantics {},
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = iconTint,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                // Title and Subtitle
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = displayTitle,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (subtitle.isNotBlank() && subtitle != displayTitle) {
                        Text(
                            text = subtitle,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = cardMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Download Button
                IconButton(
                    onClick = onDownload,
                    modifier = Modifier.minTouchTarget(48.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Download,
                            contentDescription = L.drawer.artifactsDrawer.downloadToDownloadsAntigem,
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
            }

            // Summary box if present
            if (artifact.summary.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = summaryBg,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = artifact.summary,
                        fontSize = 11.5.sp,
                        lineHeight = 16.sp,
                        color = summaryText,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        }
    }
}

private fun isImageArtifact(artifact: ArtifactSnapshot): Boolean {
    val str = "${artifact.name} ${artifact.absoluteUri}".lowercase()
    return str.endsWith(".png") || str.endsWith(".jpg") || str.endsWith(".jpeg") ||
            str.endsWith(".webp") || str.endsWith(".gif") || str.endsWith(".bmp") ||
            str.contains(".png?") || str.contains(".jpg?") || str.contains(".jpeg?")
}
