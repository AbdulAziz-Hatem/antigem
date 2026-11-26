package ai.antigem.ui.components

import android.content.Context
import android.view.View
import android.view.accessibility.AccessibilityManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import ai.antigem.i18n.L
import java.text.NumberFormat

/**
 * Whether the hosting persistent screen is currently the active, visible screen.
 * Interop AndroidView hosts use this to update [View.setImportantForAccessibility] so off-screen
 * views never leak into TalkBack exploration.
 */
val LocalScreenAccessible = compositionLocalOf { true }

/**
 * Isolates off-screen persistent screens from both Compose semantics and keyboard/D-pad focus
 * when hidden via graphicsLayer transforms.
 */
fun Modifier.accessibleScreenVisibility(visible: Boolean): Modifier =
    if (visible) this
    else this
        .focusProperties {
            canFocus = false
            onEnter = { cancelFocusChange() }
        }
        .clearAndSetSemantics { hideFromAccessibility() }

/**
 * Ensures an interactive control meets at least the 48dp x 48dp WCAG 2.0 / Android touch target.
 */
fun Modifier.minTouchTarget(minSize: Dp = 48.dp): Modifier =
    this.sizeIn(minWidth = minSize, minHeight = minSize)

/**
 * Observes live Android AccessibilityManager enabled state so Compose UIs adapt immediately when
 * TalkBack or another accessibility service turns on or off.
 */
@Composable
fun rememberIsAccessibilityEnabled(): Boolean {
    val context = LocalContext.current
    val am = remember(context) {
        context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
    }
    var enabled by remember(am) { mutableStateOf(am?.isEnabled == true) }

    DisposableEffect(am) {
        if (am == null) return@DisposableEffect onDispose {}
        val stateListener = AccessibilityManager.AccessibilityStateChangeListener { isNowEnabled ->
            enabled = isNowEnabled || am.isTouchExplorationEnabled
        }
        val touchListener = AccessibilityManager.TouchExplorationStateChangeListener { isTouch ->
            enabled = am.isEnabled || isTouch
        }
        enabled = am.isEnabled || am.isTouchExplorationEnabled
        am.addAccessibilityStateChangeListener(stateListener)
        am.addTouchExplorationStateChangeListener(touchListener)
        onDispose {
            am.removeAccessibilityStateChangeListener(stateListener)
            am.removeTouchExplorationStateChangeListener(touchListener)
        }
    }
    return enabled
}

/**
 * Posts a polite screen-reader announcement only when an accessibility service is active.
 */
@Suppress("DEPRECATION")
fun announceForAccessibility(view: View?, text: String) {
    if (view == null || text.isBlank()) return
    val am = view.context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
    if (am?.isEnabled != true || !view.isAttachedToWindow) return
    view.announceForAccessibility(text)
}

/**
 * Computes WCAG 2.0 contrast ratio between two opaque colors (after compositing translucent colors
 * onto [background] when needed).
 */
fun wcagContrastRatio(foreground: Color, background: Color): Float {
    val bgOpaque = if (background.alpha < 1f) background.copy(alpha = 1f) else background
    val fgOpaque = if (foreground.alpha < 1f) foreground.compositeOver(bgOpaque) else foreground
    val l1 = fgOpaque.luminance() + 0.05f
    val l2 = bgOpaque.luminance() + 0.05f
    return if (l1 > l2) l1 / l2 else l2 / l1
}

/**
 * Preserves [foreground]'s hue while shifting lightness if needed so it satisfies [minRatio]
 * against [background] (composited onto [surfaceFallback] if [background] is translucent).
 */
fun ensureAccessibleContrast(
    foreground: Color,
    background: Color,
    minRatio: Float = 4.5f,
    surfaceFallback: Color = Color.Black
): Color {
    val effectiveBg = if (background.alpha < 1f) background.compositeOver(surfaceFallback) else background
    val effectiveFg = if (foreground.alpha < 1f) foreground.compositeOver(effectiveBg) else foreground
    if (wcagContrastRatio(effectiveFg, effectiveBg) >= minRatio) return effectiveFg

    val bgLum = effectiveBg.luminance()
    val towardDark = bgLum >= 0.45f
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(effectiveFg.toArgb(), hsl)
    repeat(28) {
        hsl[2] = (hsl[2] + if (towardDark) -0.04f else 0.04f).coerceIn(0f, 1f)
        val candidate = Color(ColorUtils.HSLToColor(hsl))
        if (wcagContrastRatio(candidate, effectiveBg) >= minRatio) return candidate
    }
    return if (towardDark) Color(0xFF111111) else Color.White
}

/** Formats an integer cleanly for screen-reader speech (full grouped digits instead of `1.2k`). */
fun formatCountForSpeech(count: Int): String = NumberFormat.getIntegerInstance().format(count)

/** Formats a millisecond duration into natural spoken units for TalkBack content descriptions. */
fun formatDurationForSpeech(durationMs: Long): String {
    if (durationMs <= 0L) return ""
    val totalSeconds = (durationMs + 500L) / 1000L
    val minutes = (totalSeconds / 60L).toInt()
    val seconds = (totalSeconds % 60L).toInt()
    return when {
        minutes > 0 && seconds > 0 -> L.components.accessibilityHelpers.minutesMinutesSecondsSeconds(minutes, seconds)
        minutes > 0 -> L.components.accessibilityHelpers.minutesMinutes(minutes)
        durationMs < 1000L -> L.components.accessibilityHelpers.msMilliseconds(durationMs.toInt())
        else -> L.components.accessibilityHelpers.secondsSeconds(seconds)
    }
}

/**
 * Maps abbreviated or symbol terminal extra-key labels (`ESC`, `↑`, `/`, `CTRL`, `↹`, etc.) to clear
 * localized spoken descriptions for TalkBack.
 */
fun terminalKeyAccessibilityLabel(rawLabel: String): String = when (rawLabel.trim().uppercase()) {
    "ESC" -> L.components.accessibilityHelpers.escape
    "/" -> L.components.accessibilityHelpers.slash
    "-" -> L.components.accessibilityHelpers.dash
    "HOME" -> L.components.accessibilityHelpers.home
    "↑" -> L.components.accessibilityHelpers.upArrow
    "END" -> L.components.accessibilityHelpers.end
    "PGUP", L.components.localTerminal.pgup.uppercase() -> L.components.accessibilityHelpers.pageUp
    "↹", "TAB" -> L.components.accessibilityHelpers.tab
    "CTRL", L.components.localTerminal.ctrl.uppercase() -> L.components.accessibilityHelpers.control
    "ALT", L.components.localTerminal.alt.uppercase() -> L.components.accessibilityHelpers.alt
    "←" -> L.components.accessibilityHelpers.leftArrow
    "↓" -> L.components.accessibilityHelpers.downArrow
    "→" -> L.components.accessibilityHelpers.rightArrow
    "PGDN", L.components.localTerminal.pgdn.uppercase() -> L.components.accessibilityHelpers.pageDown
    else -> rawLabel
}

/** Returns spoken modifier key state (`Locked`, `Active`, or `Off`) for sticky terminal modifiers. */
fun terminalModifierStateLabel(isLocked: Boolean, isActive: Boolean): String = when {
    isLocked -> L.components.accessibilityHelpers.locked
    isActive -> L.components.accessibilityHelpers.active
    else -> L.components.accessibilityHelpers.off
}

/**
 * Unifies a toggle row (`Switch` or `Checkbox`) into a single >=48dp TalkBack node with proper
 * toggleable role and state. Callers should pass `onCheckedChange = null` on the inner widget.
 */
fun Modifier.accessibleToggleRow(
    checked: Boolean,
    role: Role = Role.Switch,
    onClick: () -> Unit
): Modifier = this
    .minTouchTarget(48.dp)
    .clickable(role = role, onClick = onClick)
    .semantics(mergeDescendants = true) {
        toggleableState = if (checked) ToggleableState.On else ToggleableState.Off
    }

/**
 * Unifies a selectable row (`RadioButton` or option card) into a single >=48dp TalkBack node with
 * `selected` state semantics.
 */
fun Modifier.accessibleSelectionRow(
    selected: Boolean,
    role: Role = Role.RadioButton,
    onClick: () -> Unit
): Modifier = this
    .minTouchTarget(48.dp)
    .clickable(role = role, onClick = onClick)
    .semantics(mergeDescendants = true) {
        this.selected = selected
    }

/** Computes an on-surface muted color guaranteed to meet WCAG AA contrast against [background]. */
@Composable
fun accessibleMutedColor(
    alpha: Float = 0.72f,
    background: Color = MaterialTheme.colorScheme.surface,
    minRatio: Float = 4.5f,
    surfaceFallback: Color = MaterialTheme.colorScheme.surface
): Color = ensureAccessibleContrast(
    foreground = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
    background = background,
    minRatio = minRatio,
    surfaceFallback = surfaceFallback
)
