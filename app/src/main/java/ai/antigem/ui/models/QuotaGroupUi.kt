package ai.antigem.ui.models

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.antigem.domain.model.QuotaGroup
import ai.antigem.domain.model.QuotaPillWindow
import ai.antigem.domain.model.QuotaWindowUi
import ai.antigem.domain.model.quotaPercentText
import ai.antigem.theme.QuotaAmber
import ai.antigem.theme.QuotaGreen
import ai.antigem.theme.QuotaRed
import ai.antigem.ui.agents.SegmentedSelector
import ai.antigem.ui.components.accessibleMutedColor
import ai.antigem.ui.components.minTouchTarget
import ai.antigem.i18n.L

/** Green / amber / red by the share left. */
fun quotaLeftColor(remaining: Float): Color = when {
    remaining > 0.5f -> QuotaGreen
    remaining > 0.2f -> QuotaAmber
    else -> QuotaRed
}

/** "5h ▬▬▬▭ 85% · 2h 4m": a limit's share left as a small meter. */
@Composable
fun QuotaMeter(window: QuotaWindowUi, modifier: Modifier = Modifier) {
    val pctText = quotaPercentText(window.remaining, window.oneDecimal)
    val color = quotaLeftColor(window.remaining)
    val fill by animateFloatAsState(window.remaining.coerceIn(0f, 1f), label = "quota_meter")
    val time = formatCleanCountdown(window.resetIso, window.countdown)
    val muted = MaterialTheme.colorScheme.onSurface
    val secondaryText = accessibleMutedColor(alpha = 0.72f)
    Surface(
        modifier = modifier.semantics(mergeDescendants = true) {},
        shape = RoundedCornerShape(8.dp),
        color = muted.copy(alpha = 0.04f),
        border = BorderStroke(1.dp, muted.copy(alpha = 0.07f))
    ) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(window.label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = muted.copy(alpha = 0.85f))
            Spacer(Modifier.width(6.dp))
            Box(Modifier.width(30.dp).height(5.dp).clip(RoundedCornerShape(3.dp)).background(muted.copy(alpha = 0.1f))) {
                Box(Modifier.fillMaxWidth(fill).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(color))
            }
            Spacer(Modifier.width(6.dp))
            Text(pctText, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = color)
            if (time.isNotBlank()) {
                Text(" · $time", fontSize = 11.sp, color = secondaryText, maxLines = 1)
            }
        }
    }
}

/** Section header of a quota group: accent dot, name, model count, then its limits. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuotaGroupHeader(
    title: String,
    accent: Color,
    count: Int,
    windows: List<QuotaWindowUi>,
    modifier: Modifier = Modifier
) {
    val mutedHeaderColor = accessibleMutedColor(alpha = 0.75f)
    Column(modifier.fillMaxWidth().padding(top = 18.dp, bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(accent).clearAndSetSemantics {})
            Spacer(Modifier.width(8.dp))
            Text(
                title.uppercase(),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = mutedHeaderColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).semantics { heading() }
            )
            Text(
                if (count == 1) "1 model" else L.models.quotaGroupUi.countModels(count),
                fontSize = 11.sp,
                color = mutedHeaderColor
            )
        }
        if (windows.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                windows.forEach { QuotaMeter(it) }
            }
        }
    }
}

/** "Shown in chat [5h | 7d]": which limit the model pill in the chat shows. */
@Composable
fun PillWindowRow(selected: QuotaPillWindow, accent: Color, modifier: Modifier = Modifier, onSelect: (QuotaPillWindow) -> Unit) {
    Row(modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(L.models.quotaGroupUi.shownInChat, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            Text(
                L.models.quotaGroupUi.shareLeftNextToTheModel,
                fontSize = 11.sp,
                lineHeight = 14.sp,
                color = accessibleMutedColor(alpha = 0.72f)
            )
        }
        Spacer(Modifier.width(12.dp))
        SegmentedSelector(
            options = listOf(QuotaPillWindow.FIVE_HOUR to "5h", QuotaPillWindow.WEEKLY to "7d"),
            selected = selected,
            accent = accent,
            onSelect = onSelect,
            modifier = Modifier.width(108.dp)
        )
    }
}

/** Header button that turns group editing on and off. */
@Composable
fun EditGroupsAction(editing: Boolean, accent: Color, onToggle: () -> Unit) {
    val bg by animateColorAsState(if (editing) accent.copy(alpha = 0.16f) else Color.Transparent, label = "edit_groups_bg")
    IconButton(onClick = onToggle, modifier = Modifier.minTouchTarget(48.dp)) {
        Box(Modifier.size(32.dp).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
            Icon(
                if (editing) Icons.Filled.Check else Icons.Outlined.Edit,
                contentDescription = if (editing) L.models.quotaGroupUi.doneEditingGroups else L.models.quotaGroupUi.editQuotaGroups,
                tint = accent,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/** Shown while editing groups, in place of the "Shown in chat" row. */
@Composable
fun EditGroupsBanner(accent: Color, modifier: Modifier = Modifier, onDone: () -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth().padding(top = 10.dp),
        shape = RoundedCornerShape(12.dp),
        color = accent.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.3f))
    ) {
        Row(Modifier.padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Tune, null, tint = accent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(L.models.quotaGroupUi.editQuotaGroups, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    L.models.quotaGroupUi.pickWhichLimitEachModelCounts,
                    fontSize = 11.5.sp,
                    lineHeight = 15.sp,
                    color = accessibleMutedColor(alpha = 0.72f)
                )
            }
            TextButton(onClick = onDone, modifier = Modifier.minTouchTarget(48.dp)) { Text(L.common.done, color = accent, fontWeight = FontWeight.SemiBold) }
        }
    }
}

/** "⇄ Gemini 4 Argon ▾": moves a model to another quota group, or back to the automatic one. */
@Composable
fun GroupMoveButton(
    current: QuotaGroup?,
    automatic: QuotaGroup?,
    isMoved: Boolean,
    groups: List<QuotaGroup>,
    accent: Color,
    onPick: (String?) -> Unit
) {
    var open by remember { mutableStateOf(false) }
    val muted = MaterialTheme.colorScheme.onSurface
    val subColor = accessibleMutedColor(alpha = 0.72f)
    Box {
        Surface(
            onClick = { open = true },
            shape = RoundedCornerShape(8.dp),
            color = accent.copy(alpha = 0.1f),
            border = BorderStroke(1.dp, accent.copy(alpha = 0.28f)),
            modifier = Modifier.minTouchTarget(48.dp)
        ) {
            Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.SwapHoriz, null, tint = accent, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(5.dp))
                Text(current?.title ?: L.models.quotaGroupUi.noGroup, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = accent, maxLines = 1)
                if (isMoved) Text("  · moved", fontSize = 10.5.sp, color = subColor)
                Icon(Icons.Filled.ArrowDropDown, null, tint = accent, modifier = Modifier.size(18.dp))
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = {
                    Column {
                        Text(L.models.quotaGroupUi.automatic, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text(automatic?.title ?: L.models.quotaGroupUi.noGroup, fontSize = 11.5.sp, color = subColor)
                    }
                },
                leadingIcon = { CheckSlot(!isMoved, accent) },
                onClick = { open = false; onPick(null) }
            )
            HorizontalDivider(color = muted.copy(alpha = 0.08f))
            groups.forEach { g ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(g.title, fontSize = 14.sp)
                            val limits = g.headerWindows.joinToString(" · ") { "${it.label} ${quotaPercentText(it.remaining, it.oneDecimal)}" }
                            if (limits.isNotBlank()) Text(limits, fontSize = 11.5.sp, color = subColor)
                        }
                    },
                    leadingIcon = { CheckSlot(isMoved && g.id == current?.id, accent) },
                    onClick = { open = false; onPick(g.id) }
                )
            }
        }
    }
}

@Composable
private fun CheckSlot(on: Boolean, accent: Color) {
    Box(Modifier.size(18.dp), contentAlignment = Alignment.Center) {
        if (on) Icon(Icons.Filled.Check, null, tint = accent, modifier = Modifier.size(18.dp))
    }
}

/** "✓ Active" badge on the selected model. */
@Composable
fun ActiveModelBadge(accent: Color) {
    Row(
        Modifier.clip(RoundedCornerShape(6.dp)).background(accent.copy(alpha = 0.15f)).padding(horizontal = 7.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.Check, null, tint = accent, modifier = Modifier.size(12.dp))
        Spacer(Modifier.width(3.dp))
        Text(L.common.active, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = accent)
    }
}
