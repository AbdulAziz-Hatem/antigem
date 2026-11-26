package ai.antigem.ui.ide

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.antigem.data.daemon.DaemonStatus
import ai.antigem.data.daemon.IdeDaemonManager
import ai.antigem.ui.components.ensureAccessibleContrast
import ai.antigem.ui.components.minTouchTarget
import kotlinx.coroutines.launch
import ai.antigem.i18n.L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DaemonLogsDialog(
    onDismiss: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val status by IdeDaemonManager.status.collectAsState()
    val statusMessage by IdeDaemonManager.statusMessage.collectAsState()
    val logs by IdeDaemonManager.logs.collectAsState()
    val listState = rememberLazyListState()

    // Auto scroll logs to bottom when new logs arrive
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Terminal,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(L.ide.daemonLogs.goDaemonStatusLogs, style = MaterialTheme.typography.titleMedium)
                }

                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = L.common.close)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(360.dp)
            ) {
                // Status Header Card
                val statusBgColor = when (status) {
                    DaemonStatus.RUNNING -> ai.antigem.theme.AppColors.success
                    DaemonStatus.STARTING -> ai.antigem.theme.AppColors.warning
                    else -> ai.antigem.theme.AppColors.danger
                }
                val statusFgColor = ensureAccessibleContrast(Color.White, statusBgColor, 4.5f)
                val retryContainerColor = statusFgColor.copy(alpha = 0.16f)
                val retryContentColor = ensureAccessibleContrast(
                    foreground = statusFgColor,
                    background = retryContainerColor,
                    minRatio = 4.5f,
                    surfaceFallback = statusBgColor
                )
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = statusBgColor,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .semantics { liveRegion = LiveRegionMode.Polite }
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(statusFgColor, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = status.name,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = statusFgColor,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = statusMessage,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = statusFgColor
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    IdeDaemonManager.ensureDaemonStarted()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = retryContainerColor,
                                contentColor = retryContentColor
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.minTouchTarget(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(L.common.retry, fontSize = 12.sp)
                        }
                    }
                }

                Text(
                    text = L.ide.daemonLogs.liveLogOutput,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp)
                )

                // Terminal Log Console
                val logBgColor = ai.antigem.theme.AppColors.editorBg
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = logBgColor,
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                ) {
                    if (logs.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                L.ide.daemonLogs.noLogEntriesYet,
                                style = MaterialTheme.typography.bodySmall,
                                color = ensureAccessibleContrast(ai.antigem.theme.AppColors.editorMuted, logBgColor)
                            )
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(8.dp)
                        ) {
                            items(logs) { logLine ->
                                Text(
                                    text = logLine,
                                    style = LocalTextStyle.current.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        color = when {
                                            logLine.contains("🎉") || logLine.contains("✅") -> ai.antigem.theme.AppColors.success
                                            logLine.contains("❌") -> ai.antigem.theme.AppColors.danger
                                            logLine.contains("SSH Execution Output") -> ai.antigem.theme.AppColors.info
                                            else -> ai.antigem.theme.AppColors.editorText
                                        }
                                    ),
                                    modifier = Modifier.padding(vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(L.common.close)
            }
        }
    )
}
