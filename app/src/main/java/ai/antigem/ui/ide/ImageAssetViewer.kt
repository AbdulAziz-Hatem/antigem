package ai.antigem.ui.ide

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import ai.antigem.data.preferences.AuthPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.antigem.ui.components.ensureAccessibleContrast
import ai.antigem.ui.components.minTouchTarget
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URLEncoder
import ai.antigem.i18n.L

@Composable
fun ImageAssetViewer(
    filePath: String,
    fileName: String,
    modifier: Modifier = Modifier,
    onToggleXmlSource: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    // Background mode: 0 = Checkerboard, 1 = White, 2 = Dark
    var bgMode by remember { mutableIntStateOf(0) }

    val fileObj = remember(filePath) { File(filePath) }
    val encodedPath = remember(filePath) { URLEncoder.encode(filePath, "UTF-8") }
    val imageUrl = remember(encodedPath) {
        val base = AuthPreferences.currentBridgeHttpUrl.removeSuffix("/")
        "$base/api/file/read?path=$encodedPath"
    }

    // Direct local file if readable, else HTTP daemon endpoint
    val imageSource = remember(fileObj, imageUrl) {
        if (fileObj.exists() && fileObj.canRead()) fileObj else imageUrl
    }

    val ext = remember(fileName) { fileName.substringAfterLast('.', "").lowercase() }
    val isSvg = ext == "svg"

    var imageDimensions by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var fileSizeText by remember { mutableStateOf<String?>(null) }

    // Read dimensions & file size in background
    LaunchedEffect(filePath) {
        withContext(Dispatchers.IO) {
            try {
                if (fileObj.exists()) {
                    val bytes = fileObj.length()
                    fileSizeText = when {
                        bytes < 1024 -> "$bytes B"
                        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
                        else -> String.format("%.2f MB", bytes.toDouble() / (1024 * 1024))
                    }
                    if (!isSvg) {
                        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeFile(filePath, options)
                        if (options.outWidth > 0 && options.outHeight > 0) {
                            imageDimensions = Pair(options.outWidth, options.outHeight)
                        }
                    }
                }
            } catch (_: Exception) {}
        }
    }

    val imageRequest = remember(imageSource, isSvg) {
        ImageRequest.Builder(context)
            .data(imageSource)
            .apply {
                if (isSvg) {
                    decoderFactory(SvgDecoder.Factory())
                }
            }
            .crossfade(true)
            .build()
    }

    val canvasBg = if (bgMode == 1) Color.White else ai.antigem.theme.AppColors.editorBg
    val panelBg = ai.antigem.theme.AppColors.editorPanel.copy(alpha = 0.93f)
    val panelFallback = ai.antigem.theme.AppColors.editorBg
    val panelTitleColor = ensureAccessibleContrast(ai.antigem.theme.AppColors.editorText, panelBg, 4.5f, panelFallback)
    val panelSubtitleColor = ensureAccessibleContrast(ai.antigem.theme.AppColors.editorMuted, panelBg, 4.5f, panelFallback)
    val panelPrimaryColor = ensureAccessibleContrast(MaterialTheme.colorScheme.primary, panelBg, 4.5f, panelFallback)
    val errorIconTint = ensureAccessibleContrast(ai.antigem.theme.AppColors.editorMuted, canvasBg)
    val errorTextTint = ensureAccessibleContrast(ai.antigem.theme.AppColors.editorText, canvasBg)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(canvasBg)
    ) {
        // 1. Checkerboard Canvas for transparency
        if (bgMode == 0) {
            CheckerboardCanvas(modifier = Modifier.fillMaxSize())
        }

        // 2. Zoomable & Pannable Image Area
        var imageState by remember { mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Empty) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(0.2f, 10f)
                        offsetX += pan.x
                        offsetY += pan.y
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = imageRequest,
                contentDescription = L.ide.imageAssetViewer.nameZoomZoom(fileName, (scale * 100).toInt()),
                onState = { imageState = it },
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offsetX,
                        translationY = offsetY
                    )
            )

            // Loading / Error Overlay
            when (imageState) {
                is AsyncImagePainter.State.Loading -> {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(40.dp)
                    )
                }
                is AsyncImagePainter.State.Error -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.BrokenImage,
                            contentDescription = L.ide.imageAssetViewer.failedToLoadImage,
                            tint = errorIconTint,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = L.ide.imageAssetViewer.couldNotPreviewImageAsset,
                            style = MaterialTheme.typography.bodySmall,
                            color = errorTextTint
                        )
                    }
                }
                else -> {}
            }
        }

        // 3. Floating Control & Info Overlay Bar
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(12.dp)
                .fillMaxWidth(0.95f),
            shape = RoundedCornerShape(10.dp),
            color = panelBg,
            tonalElevation = 6.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Info Section
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Image,
                        contentDescription = null,
                        tint = panelPrimaryColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = fileName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = panelTitleColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val dimText = imageDimensions?.let { "${it.first} × ${it.second} px" } ?: if (isSvg) L.ide.imageAssetViewer.vectorSvg else ""
                        val sizeText = fileSizeText?.let { " • $it" } ?: ""
                        val zoomText = " • ${(scale * 100).toInt()}%"
                        Text(
                            text = "$dimText$sizeText$zoomText",
                            style = MaterialTheme.typography.labelSmall,
                            color = panelSubtitleColor,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Controls Section
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Background Canvas Switcher
                    val bgToggleTint = ensureAccessibleContrast(
                        foreground = when (bgMode) {
                            1 -> Color.White
                            2 -> ai.antigem.theme.AppColors.editorMuted
                            else -> MaterialTheme.colorScheme.primary
                        },
                        background = panelBg,
                        minRatio = 4.5f,
                        surfaceFallback = panelFallback
                    )
                    IconButton(
                        onClick = { bgMode = (bgMode + 1) % 3 },
                        modifier = Modifier.minTouchTarget(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Contrast,
                            contentDescription = L.ide.imageAssetViewer.toggleBackground,
                            tint = bgToggleTint,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = { scale = (scale - 0.25f).coerceAtLeast(0.25f) },
                        modifier = Modifier.minTouchTarget(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ZoomOut,
                            contentDescription = L.ide.imageAssetViewer.zoomOut,
                            tint = panelTitleColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = {
                            scale = 1f
                            offsetX = 0f
                            offsetY = 0f
                        },
                        modifier = Modifier
                            .minTouchTarget(48.dp)
                            .semantics { contentDescription = L.ide.imageAssetViewer.resetZoom }
                    ) {
                        Text(
                            text = "1:1",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = panelPrimaryColor
                        )
                    }
                    IconButton(
                        onClick = { scale = (scale + 0.25f).coerceAtMost(10f) },
                        modifier = Modifier.minTouchTarget(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ZoomIn,
                            contentDescription = L.ide.imageAssetViewer.zoomIn,
                            tint = panelTitleColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    if (isSvg && onToggleXmlSource != null) {
                        IconButton(
                            onClick = onToggleXmlSource,
                            modifier = Modifier.minTouchTarget(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Code,
                                contentDescription = L.ide.imageAssetViewer.viewSvgXmlSource,
                                tint = panelPrimaryColor,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CheckerboardCanvas(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val squareSize = 16.dp.toPx()
        val cols = (size.width / squareSize).toInt() + 1
        val rows = (size.height / squareSize).toInt() + 1
        val dark = ai.antigem.theme.AppColors.editorPanel
        val light = ai.antigem.theme.AppColors.editorLine

        for (c in 0 until cols) {
            for (r in 0 until rows) {
                val color = if ((c + r) % 2 == 0) dark else light
                drawRect(
                    color = color,
                    topLeft = Offset(c * squareSize, r * squareSize),
                    size = Size(squareSize, squareSize)
                )
            }
        }
    }
}
