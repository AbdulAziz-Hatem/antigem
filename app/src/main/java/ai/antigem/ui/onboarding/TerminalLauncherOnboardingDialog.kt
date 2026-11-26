package ai.antigem.ui.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import ai.antigem.ui.components.accessibleMutedColor
import ai.antigem.ui.components.accessibleSelectionRow
import ai.antigem.ui.components.accessibleToggleRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ai.antigem.R
import ai.antigem.data.preferences.TerminalLauncherManager
import ai.antigem.theme.ClaudeTerracotta
import ai.antigem.i18n.L

@Composable
fun TerminalLauncherOnboardingDialog(
    /** Position in the first-launch setup ("Step 2 of 2" after the agents). */
    step: Int = 1,
    total: Int = 1,
    initialEnabled: Boolean = false,
    initialStyle: String = TerminalLauncherManager.STYLE_ANTITERM,
    onConfirm: (enabled: Boolean, style: String) -> Unit,
    onDismiss: () -> Unit
) {
    var isEnabled by remember { mutableStateOf(initialEnabled) }
    var selectedStyle by remember { mutableStateOf(initialStyle) }
    val previewCardBg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    val mutedPreviewText = accessibleMutedColor(alpha = 0.72f, background = previewCardBg)
    val mutedToggleDesc = accessibleMutedColor(alpha = 0.72f, background = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))

    OnboardingStepScreen(
        step = step,
        total = total,
        accent = ClaudeTerracotta,
        icon = { Icon(Icons.Default.Terminal, contentDescription = null, tint = ClaudeTerracotta, modifier = Modifier.size(36.dp)) },
        title = L.onboarding.terminalLauncherOnboarding.terminalAppSetup,
        subtitle = L.onboarding.terminalLauncherOnboarding.chooseHowYouWantToOpen,
        primaryLabel = L.onboarding.terminalLauncherOnboarding.saveContinue,
        onPrimary = { onConfirm(isEnabled, selectedStyle) },
        footnote = L.onboarding.terminalLauncherOnboarding.youCanChangeThisAnytimeIn,
        onBack = onDismiss
    ) {
                // Visual Preview: Home Screen Apps Simulation
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "HOME SCREEN PREVIEW",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = ClaudeTerracotta,
                            letterSpacing = 1.sp
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Icon 1: AntiGem (Main App)
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.width(90.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(54.dp)
                                        .shadow(4.dp, RoundedCornerShape(14.dp))
                                        .clip(RoundedCornerShape(14.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    ai.antigem.ui.components.AppIconImage(
                                        contentDescription = L.common.antiGem,
                                        modifier = Modifier.matchParentSize()
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = L.common.antiGem,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = L.onboarding.terminalLauncherOnboarding.chatIde,
                                    fontSize = 9.5.sp,
                                    color = mutedPreviewText
                                )
                            }

                            // Plus Divider
                            Text(
                                text = "+",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = mutedPreviewText,
                                modifier = Modifier.clearAndSetSemantics {}
                            )

                            // Icon 2: Terminal App (Dynamic)
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .width(90.dp)
                                    .alpha(if (isEnabled) 1f else 0.4f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(54.dp)
                                        .shadow(if (isEnabled) 4.dp else 0.dp, RoundedCornerShape(14.dp))
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(
                                            if (selectedStyle == TerminalLauncherManager.STYLE_TERMUX) {
                                                Color.Black
                                            } else {
                                                Color.Transparent
                                            }
                                        )
                                        .padding(if (selectedStyle == TerminalLauncherManager.STYLE_TERMUX) 6.dp else 0.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (selectedStyle == TerminalLauncherManager.STYLE_TERMUX) {
                                        Image(
                                            painter = painterResource(id = R.drawable.ic_termux_foreground),
                                            contentDescription = L.common.termux,
                                            modifier = Modifier.size(38.dp)
                                        )
                                    } else {
                                        ai.antigem.ui.components.AppIconImage(
                                            contentDescription = L.common.antiTerm,
                                            modifier = Modifier.matchParentSize()
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = if (!isEnabled) L.onboarding.terminalLauncherOnboarding.hidden else if (selectedStyle == TerminalLauncherManager.STYLE_TERMUX) L.common.termux else L.common.antiTerm,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (!isEnabled) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (isEnabled) L.common.terminal else L.onboarding.terminalLauncherOnboarding.inAppOnly,
                                    fontSize = 9.5.sp,
                                    color = mutedPreviewText
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Toggle Switch: Separate Terminal App Icon
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .accessibleToggleRow(checked = isEnabled, role = Role.Switch) { isEnabled = !isEnabled }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(
                                text = L.onboarding.terminalLauncherOnboarding.standaloneTerminalAppIcon,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = L.onboarding.terminalLauncherOnboarding.placesADedicatedIconInYour,
                                fontSize = 11.sp,
                                color = mutedToggleDesc
                            )
                        }

                        Switch(
                            checked = isEnabled,
                            onCheckedChange = null,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                checkedTrackColor = ClaudeTerracotta
                            )
                        )
                    }
                }

                // Icon & Name Style Selector (Shown when enabled)
                AnimatedVisibility(
                    visible = isEnabled,
                    enter = fadeIn() + slideInVertically(animationSpec = tween(200)),
                    exit = fadeOut() + slideOutVertically(animationSpec = tween(200))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                            .animateContentSize()
                            .selectableGroup()
                    ) {
                        Text(
                            text = L.onboarding.terminalLauncherOnboarding.selectIconAppName,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = accessibleMutedColor(alpha = 0.8f),
                            modifier = Modifier.padding(start = 2.dp, bottom = 8.dp)
                        )

                        // Option 1: AntiTerm (Branded)
                        val isAntiTermSelected = selectedStyle == TerminalLauncherManager.STYLE_ANTITERM
                        val antiTermBg = if (isAntiTermSelected) ClaudeTerracotta.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = antiTermBg,
                            border = BorderStroke(
                                1.5.dp,
                                if (isAntiTermSelected) ClaudeTerracotta else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .accessibleSelectionRow(selected = isAntiTermSelected, role = Role.RadioButton) {
                                    selectedStyle = TerminalLauncherManager.STYLE_ANTITERM
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(RoundedCornerShape(10.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    ai.antigem.ui.components.AppIconImage(
                                        contentDescription = L.common.antiTerm,
                                        modifier = Modifier.matchParentSize()
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = L.common.antiTerm,
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = L.onboarding.terminalLauncherOnboarding.modernUnifiedIconMatchingAntigem,
                                        fontSize = 11.sp,
                                        color = accessibleMutedColor(alpha = 0.72f, background = antiTermBg)
                                    )
                                }

                                RadioButton(
                                    selected = isAntiTermSelected,
                                    onClick = null,
                                    colors = RadioButtonDefaults.colors(selectedColor = ClaudeTerracotta)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Option 2: Termux (Classic)
                        val isTermuxSelected = selectedStyle == TerminalLauncherManager.STYLE_TERMUX
                        val termuxBg = if (isTermuxSelected) ClaudeTerracotta.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = termuxBg,
                            border = BorderStroke(
                                1.5.dp,
                                if (isTermuxSelected) ClaudeTerracotta else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .accessibleSelectionRow(selected = isTermuxSelected, role = Role.RadioButton) {
                                    selectedStyle = TerminalLauncherManager.STYLE_TERMUX
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color.Black)
                                        .padding(4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Image(
                                        painter = painterResource(id = R.drawable.ic_termux_foreground),
                                        contentDescription = L.common.termux,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = L.common.termux,
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = L.onboarding.terminalLauncherOnboarding.classicTerminalPromptIconFamiliarName,
                                        fontSize = 11.sp,
                                        color = accessibleMutedColor(alpha = 0.72f, background = termuxBg)
                                    )
                                }

                                RadioButton(
                                    selected = isTermuxSelected,
                                    onClick = null,
                                    colors = RadioButtonDefaults.colors(selectedColor = ClaudeTerracotta)
                                )
                            }
                        }
                    }
                }

    }
}
