package ai.antigem.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BrightnessAuto
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.antigem.theme.ThemeManager
import ai.antigem.i18n.L
import ai.antigem.ui.components.accessibleMutedColor
import ai.antigem.ui.components.accessibleSelectionRow
import ai.antigem.ui.components.minTouchTarget

/**
 * A floating theme picker (opened from the sidebar). It covers only the bottom of the screen and takes no touches
 * outside itself, so the sidebar and the chat stay visible (and usable) behind it: a tap on a theme applies it at once
 * and the whole app shows it right away. It lists the themes of the side on screen (light or dark); the mode row
 * switches sides. Back or ✕ closes it.
 */
@Composable
fun ThemeQuickPanel(themeMode: String, onSetThemeMode: (String) -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier) {
    BackHandler(onBack = onClose)
    val dark = ThemeManager.isDark
    val selectedId = if (dark) ThemeManager.darkId else ThemeManager.lightId
    val custom = ThemeManager.customThemes
    val themes = remember(custom, dark) { ThemeManager.all().filter { it.dark == dark } }
    val listState = rememberLazyListState()
    // show the theme in use (with one neighbour before it) when the panel opens or the side changes
    LaunchedEffect(dark) {
        val i = themes.indexOfFirst { it.id == selectedId }
        if (i > 0) listState.scrollToItem(i - 1)
    }
    val onSurface = MaterialTheme.colorScheme.onSurface
    val panelBg = MaterialTheme.colorScheme.surfaceContainerHigh
    val mutedColor = accessibleMutedColor(alpha = 0.72f, background = panelBg)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 10.dp, vertical = 10.dp)
            .semantics {
                paneTitle = L.settings.themeQuick.themeQuickPanel
                isTraversalGroup = true
            },
        shape = RoundedCornerShape(22.dp),
        color = panelBg,
        border = BorderStroke(1.dp, onSurface.copy(alpha = 0.12f)),
        shadowElevation = 18.dp
    ) {
        Column(Modifier.padding(top = 10.dp, bottom = 12.dp)) {
            Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Palette, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(7.dp))
                Text(
                    L.common.themes,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = onSurface,
                    modifier = Modifier.weight(1f).semantics { heading() }
                )
                IconButton(onClick = onClose, modifier = Modifier.minTouchTarget(48.dp)) {
                    Icon(Icons.Outlined.Close, L.common.close, tint = mutedColor, modifier = Modifier.size(18.dp))
                }
            }
            ModeToggle(
                themeMode = themeMode,
                panelBg = panelBg,
                onSetThemeMode = onSetThemeMode,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
            )
            Text(
                L.settings.themeQuick.tapAThemeToTryIt,
                fontSize = 11.sp, color = mutedColor,
                modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 2.dp, bottom = 8.dp)
            )
            LazyRow(
                state = listState,
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.selectableGroup()
            ) {
                items(themes, key = { it.id }) { t ->
                    ThemeCard(
                        t, t.id == selectedId, MaterialTheme.colorScheme.surface, Modifier.width(138.dp),
                        onClick = { ThemeManager.select(t, switchMode = false) }, onDelete = null
                    )
                }
            }
        }
    }
}

/** Light / Dark / Auto as three small pills (the same setting as Settings → Appearance). */
@Composable
private fun ModeToggle(themeMode: String, panelBg: Color, onSetThemeMode: (String) -> Unit, modifier: Modifier = Modifier) {
    val options = listOf(
        Triple("LIGHT", L.settings.themeQuick.light, Icons.Outlined.LightMode),
        Triple("DARK", L.settings.themeQuick.dark, Icons.Outlined.DarkMode),
        Triple("SYSTEM", L.common.auto, Icons.Outlined.BrightnessAuto)
    )
    Row(
        modifier
            .horizontalScroll(rememberScrollState())
            .selectableGroup()
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            .padding(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        options.forEach { (mode, label, icon) -> ModePill(label, icon, themeMode == mode, panelBg) { onSetThemeMode(mode) } }
    }
}

@Composable
private fun ModePill(label: String, icon: ImageVector, selected: Boolean, panelBg: Color, onClick: () -> Unit) {
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary else accessibleMutedColor(alpha = 0.8f, background = panelBg)
    Row(
        Modifier
            .minTouchTarget(48.dp)
            .clip(RoundedCornerShape(50))
            .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
            .accessibleSelectionRow(selected = selected, role = Role.Tab, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = fg, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, fontSize = 11.5.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium, color = fg)
    }
}
