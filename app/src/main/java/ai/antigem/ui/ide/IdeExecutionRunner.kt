package ai.antigem.ui.ide

import android.content.Context
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import ai.antigem.data.local.LocalPtySession
import ai.antigem.theme.ClaudeTerracotta
import ai.antigem.ui.components.LocalScreenAccessible
import ai.antigem.ui.components.announceForAccessibility
import ai.antigem.ui.components.ensureAccessibleContrast
import ai.antigem.ui.components.minTouchTarget
import ai.antigem.ui.components.terminalKeyAccessibilityLabel
import ai.antigem.ui.components.terminalModifierStateLabel
import com.termux.terminal.TerminalSession
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient
import java.util.UUID
import kotlinx.coroutines.flow.first
import ai.antigem.i18n.L

data class IdeExecutionRequest(
    val id: String = UUID.randomUUID().toString(),
    val profileName: String,
    val command: String,
    val workingDir: String? = null
)

private enum class IdeKeyModifierState { OFF, ONE_SHOT, LOCKED }

@Composable
fun IdeExecutionRunnerSheet(
    request: IdeExecutionRequest,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val hostView = LocalView.current
    val screenAccessible = LocalScreenAccessible.current

    var sessionKey by remember { mutableStateOf(request.id) }
    var terminalTextSize by remember { mutableIntStateOf((13 * density.density).toInt().coerceIn(16, 60)) }
    var currentTerminalView by remember { mutableStateOf<TerminalView?>(null) }
    var ctrlState by remember { mutableStateOf(IdeKeyModifierState.OFF) }
    var altState by remember { mutableStateOf(IdeKeyModifierState.OFF) }

    // Remote mode: the command runs on the server, in its own tmux session reached over SSH (a PC in use only when it
    // allows a shell for this phone; otherwise here)
    val remoteRun = ai.antigem.data.preferences.AuthPreferences.remoteShell
    val runSessionName = remember(sessionKey) { "antigem-run-" + UUID.randomUUID().toString().take(8) }

    val ptySession = remember(sessionKey) {
        val displayCmd = request.command
        val escapedCmd = displayCmd.replace("\\", "\\\\").replace("\"", "\\\"").replace("$", "\\$")
        val wrappedCmd = "echo -e \"\\033[1;32m▶ $escapedCmd\\033[0m\"; $displayCmd"
        if (remoteRun) {
            val prefs = ai.antigem.data.preferences.AuthPreferences(context)
            val (host, port, user, pass) = kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
                listOf(
                    prefs.termuxSshHost.first(), prefs.termuxSshPort.first().toString(),
                    prefs.termuxSshUser.first(), prefs.termuxSshPass.first()
                )
            }
            val dir = request.workingDir?.takeIf { it.isNotBlank() }
            val cdPart = if (dir != null) "cd '" + dir.replace("'", "'\\''") + "' 2>/dev/null; " else ""
            LocalPtySession(
                id = "ide_run_${UUID.randomUUID()}",
                initialTitle = request.profileName,
                context = context,
                isSsh = true,
                sshHost = host,
                sshPort = port.toIntOrNull() ?: 22,
                sshUser = user,
                sshPass = pass,
                tmuxSessionName = runSessionName,
                initialCommand = cdPart + wrappedCmd
            )
        } else {
            LocalPtySession(
                id = "ide_run_${UUID.randomUUID()}",
                initialTitle = request.profileName,
                context = context,
                forceShell = "bash",
                initialWorkingDir = request.workingDir,
                initialCommand = wrappedCmd
            )
        }
    }

    val isExited by ptySession.isExited.collectAsState()

    DisposableEffect(ptySession) {
        onDispose {
            if (remoteRun) {
                ptySession.close()
                ai.antigem.data.remote.RemoteServerManager.killTmuxSession(runSessionName)
            } else {
                closeWithChildren(ptySession)
            }
        }
    }

    // The session is its own TerminalSessionClient, so the view only repaints when told to
    DisposableEffect(ptySession, currentTerminalView) {
        val tv = currentTerminalView
        if (tv == null) return@DisposableEffect onDispose {}
        val listener: () -> Unit = { tv.post { tv.onScreenUpdated(); tv.invalidate() } }
        ptySession.addTextChangedListener(listener)
        onDispose { ptySession.removeTextChangedListener(listener) }
    }

    fun sendKeyToTerminal(keyCode: Int, fallbackString: String) {
        val tv = currentTerminalView ?: return
        val currentSession = ptySession.terminalSession

        val isCtrl = ctrlState != IdeKeyModifierState.OFF
        val isAlt = altState != IdeKeyModifierState.OFF

        val event = KeyEvent(
            0L, 0L, KeyEvent.ACTION_DOWN, keyCode, 0,
            (if (isCtrl) KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON else 0) or
                    (if (isAlt) KeyEvent.META_ALT_ON or KeyEvent.META_ALT_LEFT_ON else 0)
        )

        val handled = tv.mClient?.onKeyDown(keyCode, event, currentSession) == true
        if (!handled) {
            ptySession.write(fallbackString)
        }

        if (ctrlState == IdeKeyModifierState.ONE_SHOT) ctrlState = IdeKeyModifierState.OFF
        if (altState == IdeKeyModifierState.ONE_SHOT) altState = IdeKeyModifierState.OFF
    }

    fun announceModifier(label: String, state: IdeKeyModifierState) {
        val spoken = terminalKeyAccessibilityLabel(label)
        val stateText = terminalModifierStateLabel(
            isLocked = state == IdeKeyModifierState.LOCKED,
            isActive = state != IdeKeyModifierState.OFF
        )
        announceForAccessibility(hostView, "$spoken $stateText")
    }

    val panelBg = ai.antigem.theme.AppColors.editorPanel
    val headerTitleColor = ensureAccessibleContrast(ai.antigem.theme.AppColors.editorText, panelBg)
    val headerActionTint = ensureAccessibleContrast(ai.antigem.theme.AppColors.editorText, panelBg)
    val headerMutedTint = ensureAccessibleContrast(ai.antigem.theme.AppColors.editorMuted, panelBg)
    val statusColor = ensureAccessibleContrast(
        if (!isExited) ai.antigem.theme.AppColors.success else ai.antigem.theme.AppColors.danger,
        panelBg
    )

    Surface(
        color = ai.antigem.theme.AppColors.editorBg,
        modifier = modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Header Bar
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(panelBg)
                    .border(BorderStroke(0.5.dp, ai.antigem.theme.AppColors.editorBorder))
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(statusColor)
                    )

                    Column {
                        Text(
                            text = request.profileName,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = headerTitleColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (!isExited) L.ide.executionRunner.runningCommand(request.command) else L.ide.executionRunner.finishedExited,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = statusColor,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    // Re-run / Restart
                    IconButton(
                        onClick = {
                            closeWithChildren(ptySession)
                            sessionKey = UUID.randomUUID().toString()
                        },
                        modifier = Modifier.minTouchTarget(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = L.common.restart,
                            tint = headerActionTint,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Stop / Interrupt (Ctrl+C)
                    IconButton(
                        onClick = {
                            if (!isExited) {
                                ptySession.write("\u0003")
                            } else {
                                ptySession.close()
                            }
                        },
                        modifier = Modifier.minTouchTarget(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = L.common.stop,
                            tint = if (!isExited) statusColor else headerMutedTint,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Clear Screen
                    IconButton(
                        onClick = {
                            try {
                                ptySession.terminalSession.reset()
                                ptySession.write("\u000c")
                            } catch (_: Exception) {}
                        },
                        modifier = Modifier.minTouchTarget(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ClearAll,
                            contentDescription = L.common.clear,
                            tint = headerActionTint,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Close
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.minTouchTarget(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = L.common.close,
                            tint = headerActionTint,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
            }

            // Interactive Native Termux Terminal View
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(ai.antigem.theme.AppColors.terminalBg)
                    .padding(horizontal = 4.dp, vertical = 2.dp)
                    .semantics { contentDescription = L.ide.executionRunner.executionOutput }
            ) {
                key(sessionKey) {
                    AndroidView(
                        factory = { ctx ->
                            TerminalView(ctx, null).apply {
                                setTextSize(terminalTextSize)
                                isFocusable = screenAccessible
                                isFocusableInTouchMode = screenAccessible
                                contentDescription = L.ide.executionRunner.executionTerminal
                                importantForAccessibility = if (screenAccessible) {
                                    View.IMPORTANT_FOR_ACCESSIBILITY_YES
                                } else {
                                    View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                                }

                                setTerminalViewClient(object : TerminalViewClient {
                                    override fun onScale(scale: Float): Float {
                                        if (scale < 0.92f || scale > 1.08f) {
                                            val doIncrease = scale > 1.0f
                                            val newSize = if (doIncrease) terminalTextSize + 1 else terminalTextSize - 1
                                            if (newSize in 16..60) {
                                                terminalTextSize = newSize
                                                this@apply.setTextSize(terminalTextSize)
                                            }
                                            return 1.0f
                                        }
                                        return scale
                                    }

                                    override fun onSingleTapUp(e: MotionEvent) {
                                        this@apply.requestFocus()
                                        val imm = ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                                        imm.showSoftInput(this@apply, 0)
                                    }

                                    override fun shouldBackButtonBeMappedToEscape(): Boolean = false
                                    override fun shouldEnforceCharBasedInput(): Boolean = false
                                    override fun shouldUseCtrlSpaceWorkaround(): Boolean = false
                                    override fun isTerminalViewSelected(): Boolean = true
                                    override fun copyModeChanged(copyMode: Boolean) {}

                                    override fun onKeyDown(keyCode: Int, e: KeyEvent, session: TerminalSession): Boolean {
                                        val isCtrl = e.isCtrlPressed || ctrlState != IdeKeyModifierState.OFF
                                        val isAlt = e.isAltPressed || altState != IdeKeyModifierState.OFF

                                        if (isCtrl && keyCode == KeyEvent.KEYCODE_C) {
                                            ptySession.write("\u0003")
                                            if (ctrlState == IdeKeyModifierState.ONE_SHOT) ctrlState = IdeKeyModifierState.OFF
                                            return true
                                        }
                                        return false
                                    }

                                    override fun onKeyUp(keyCode: Int, e: KeyEvent): Boolean = false
                                    override fun onLongPress(event: MotionEvent): Boolean = false
                                    override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession): Boolean = false

                                    override fun readControlKey(): Boolean = ctrlState != IdeKeyModifierState.OFF
                                    override fun readAltKey(): Boolean = altState != IdeKeyModifierState.OFF
                                    override fun readShiftKey(): Boolean = false
                                    override fun readFnKey(): Boolean = false

                                    override fun onEmulatorSet() {
                                        post {
                                            onScreenUpdated()
                                            invalidate()
                                        }
                                    }

                                    override fun logError(tag: String, message: String) {}
                                    override fun logWarn(tag: String, message: String) {}
                                    override fun logInfo(tag: String, message: String) {}
                                    override fun logDebug(tag: String, message: String) {}
                                    override fun logVerbose(tag: String, message: String) {}
                                    override fun logStackTraceWithMessage(tag: String, message: String, e: Exception) {}
                                    override fun logStackTrace(tag: String, e: Exception) {}
                                })

                                setTerminalInputListener(object : TerminalView.TerminalInputListener {
                                    override fun onTerminalInput(text: String) {
                                        ptySession.write(text)
                                    }
                                    override fun onTerminalInputCodePoint(prependEscape: Boolean, codePoint: Int) {
                                        ptySession.writeCodePoint(prependEscape, codePoint)
                                    }
                                })

                                setTerminalSizeListener { cols, rows, widthPx, heightPx ->
                                    ptySession.updateSize(cols, rows, widthPx, heightPx)
                                }

                                attachSession(ptySession.terminalSession)
                                currentTerminalView = this

                                post {
                                    requestFocus()
                                    val imm = ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                                    imm.showSoftInput(this, 0)
                                }
                            }
                        },
                        update = { tv ->
                            currentTerminalView = tv
                            tv.isFocusable = screenAccessible
                            tv.isFocusableInTouchMode = screenAccessible
                            tv.contentDescription = L.ide.executionRunner.executionTerminal
                            tv.importantForAccessibility = if (screenAccessible) {
                                View.IMPORTANT_FOR_ACCESSIBILITY_YES
                            } else {
                                View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                            }
                            tv.setTextSize(terminalTextSize)
                            tv.setTerminalInputListener(object : TerminalView.TerminalInputListener {
                                override fun onTerminalInput(text: String) {
                                    ptySession.write(text)
                                }
                                override fun onTerminalInputCodePoint(prependEscape: Boolean, codePoint: Int) {
                                    ptySession.writeCodePoint(prependEscape, codePoint)
                                }
                            })
                            tv.setTerminalSizeListener { cols, rows, widthPx, heightPx ->
                                ptySession.updateSize(cols, rows, widthPx, heightPx)
                            }
                            if (tv.currentSession != ptySession.terminalSession) {
                                tv.attachSession(ptySession.terminalSession)
                            }
                            tv.post {
                                tv.onScreenUpdated()
                                tv.invalidate()
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // Bottom Extra Keys Bar (Quick access to terminal navigation)
            Surface(
                color = ai.antigem.theme.AppColors.editorBg,
                border = BorderStroke(0.5.dp, ai.antigem.theme.AppColors.editorBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 3.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    IdeTermKey(label = "ESC", modifier = Modifier.weight(1f)) {
                        sendKeyToTerminal(KeyEvent.KEYCODE_ESCAPE, "\u001B")
                    }
                    IdeTermKey(label = "TAB", modifier = Modifier.weight(1f)) {
                        sendKeyToTerminal(KeyEvent.KEYCODE_TAB, "\t")
                    }
                    IdeTermKey(
                        label = L.ide.executionRunner.ctrl,
                        isActive = ctrlState != IdeKeyModifierState.OFF,
                        isLocked = ctrlState == IdeKeyModifierState.LOCKED,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            ctrlState = if (ctrlState == IdeKeyModifierState.OFF) IdeKeyModifierState.ONE_SHOT else IdeKeyModifierState.OFF
                            announceModifier(L.ide.executionRunner.ctrl, ctrlState)
                        },
                        onLongClick = {
                            ctrlState = if (ctrlState == IdeKeyModifierState.LOCKED) IdeKeyModifierState.OFF else IdeKeyModifierState.LOCKED
                            announceModifier(L.ide.executionRunner.ctrl, ctrlState)
                        }
                    )
                    IdeTermKey(
                        label = L.ide.executionRunner.alt,
                        isActive = altState != IdeKeyModifierState.OFF,
                        isLocked = altState == IdeKeyModifierState.LOCKED,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            altState = if (altState == IdeKeyModifierState.OFF) IdeKeyModifierState.ONE_SHOT else IdeKeyModifierState.OFF
                            announceModifier(L.ide.executionRunner.alt, altState)
                        },
                        onLongClick = {
                            altState = if (altState == IdeKeyModifierState.LOCKED) IdeKeyModifierState.OFF else IdeKeyModifierState.LOCKED
                            announceModifier(L.ide.executionRunner.alt, altState)
                        }
                    )
                    IdeTermKey(label = "↑", modifier = Modifier.weight(1f)) {
                        sendKeyToTerminal(KeyEvent.KEYCODE_DPAD_UP, "\u001B[A")
                    }
                    IdeTermKey(label = "↓", modifier = Modifier.weight(1f)) {
                        sendKeyToTerminal(KeyEvent.KEYCODE_DPAD_DOWN, "\u001B[B")
                    }
                    IdeTermKey(label = "←", modifier = Modifier.weight(1f)) {
                        sendKeyToTerminal(KeyEvent.KEYCODE_DPAD_LEFT, "\u001B[D")
                    }
                    IdeTermKey(label = "→", modifier = Modifier.weight(1f)) {
                        sendKeyToTerminal(KeyEvent.KEYCODE_DPAD_RIGHT, "\u001B[C")
                    }
                }
            }
        }
    }
}

/** Kills the command and everything it started (see RunManager.killTree). */
private fun closeWithChildren(session: LocalPtySession) = ai.antigem.data.run.RunManager.killTree(session)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun IdeTermKey(
    label: String,
    isActive: Boolean = false,
    isLocked: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    onLongClick: (() -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val spokenLabel = terminalKeyAccessibilityLabel(label)
    val hasModifierState = onLongClick != null || isActive || isLocked

    val backgroundColor = when {
        isPressed -> ai.antigem.theme.AppColors.editorLine
        isLocked -> ClaudeTerracotta.copy(alpha = 0.5f)
        isActive -> ClaudeTerracotta.copy(alpha = 0.25f)
        else -> ai.antigem.theme.AppColors.editorPanel
    }

    val rawTextColor = when {
        isPressed || isLocked -> Color.White
        isActive -> ClaudeTerracotta
        else -> ai.antigem.theme.AppColors.editorText
    }
    val textColor = ensureAccessibleContrast(
        foreground = rawTextColor,
        background = backgroundColor,
        minRatio = 4.5f,
        surfaceFallback = ai.antigem.theme.AppColors.editorBg
    )

    val border = when {
        isLocked -> BorderStroke(1.dp, ClaudeTerracotta)
        isActive -> BorderStroke(0.5.dp, ClaudeTerracotta.copy(alpha = 0.5f))
        else -> BorderStroke(0.5.dp, ai.antigem.theme.AppColors.editorBorder)
    }

    Surface(
        modifier = modifier
            .minTouchTarget(48.dp)
            .clip(RoundedCornerShape(6.dp))
            .combinedClickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                role = Role.Button,
                onClick = onClick,
                onLongClick = onLongClick
            )
            .semantics {
                contentDescription = spokenLabel
                if (hasModifierState) {
                    stateDescription = terminalModifierStateLabel(isLocked, isActive)
                }
            },
        color = backgroundColor,
        border = border,
        shape = RoundedCornerShape(6.dp)
    ) {
        Box(
            modifier = Modifier.defaultMinSize(minHeight = 48.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                fontSize = 11.5.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = if (isActive || isLocked || isPressed) FontWeight.Bold else FontWeight.SemiBold,
                color = textColor
            )
        }
    }
}
