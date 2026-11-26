package ai.antigem.ui.drawer

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Token
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import ai.antigem.data.remote.AgyHubClient
import ai.antigem.ui.components.accessibleMutedColor
import ai.antigem.ui.components.ensureAccessibleContrast
import ai.antigem.ui.components.label
import ai.antigem.ui.components.minTouchTarget
import ai.antigem.theme.ClaudeTerracotta
import ai.antigem.theme.QuotaGreen
import ai.antigem.i18n.L

/**
 * Safely parses base64 data URIs ("data:image/jpeg;base64,...") into ByteArray for Coil,
 * or returns original string for standard HTTP/HTTPS URLs.
 */
fun parseProfileAvatarModel(profilePictureUrl: String?): Any? {
    if (profilePictureUrl.isNullOrBlank()) return null
    if (profilePictureUrl.startsWith("data:image/", ignoreCase = true)) {
        try {
            val commaIdx = profilePictureUrl.indexOf(',')
            if (commaIdx != -1) {
                val base64Part = profilePictureUrl.substring(commaIdx + 1).trim()
                return android.util.Base64.decode(base64Part, android.util.Base64.DEFAULT)
            }
        } catch (_: Exception) {}
    }
    return profilePictureUrl
}

@Composable
fun ProfileDetailDialog(
    authInfo: AgyHubClient.AgyAuthInfo,
    onDismiss: () -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    var confirmSignOut by remember { mutableStateOf(false) }
    if (confirmSignOut) {
        ai.antigem.ui.agents.ConfirmSignOutDialog(
            agentName = ai.antigem.domain.model.AgentKind.AGY.label(),
            account = authInfo.email,
            onConfirm = {
                onLogout()
                onDismiss()
            },
            onDismiss = { confirmSignOut = false }
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        val surfaceBg = MaterialTheme.colorScheme.surface
        val mutedText = accessibleMutedColor(alpha = 0.72f, background = surfaceBg)
        val mutedIcon = accessibleMutedColor(alpha = 0.72f, background = surfaceBg, minRatio = 3.0f)
        val badgeBg = ClaudeTerracotta.copy(alpha = 0.12f)
        val accessibleTerracottaText = ensureAccessibleContrast(ClaudeTerracotta, surfaceBg, 4.5f, surfaceBg)
        val accessibleBadgeText = ensureAccessibleContrast(ClaudeTerracotta, badgeBg, 4.5f, surfaceBg)
        val accessibleTerracottaIcon = ensureAccessibleContrast(ClaudeTerracotta, surfaceBg, 3.0f, surfaceBg)
        val accessibleError = ensureAccessibleContrast(MaterialTheme.colorScheme.error, surfaceBg, 4.5f, surfaceBg)

        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = surfaceBg
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .semantics { isTraversalGroup = true }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top close button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.minTouchTarget(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = L.common.close,
                            tint = mutedIcon
                        )
                    }
                }

                // Avatar
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(parseProfileAvatarModel(authInfo.profilePictureUrl))
                        .crossfade(true)
                        .build(),
                    contentDescription = L.drawer.profileDetail.profilePicture,
                    modifier = Modifier
                        .size(76.dp)
                        .clip(CircleShape)
                        .border(2.5.dp, ClaudeTerracotta, CircleShape),
                    contentScale = ContentScale.Crop,
                    loading = {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(ClaudeTerracotta),
                            contentAlignment = Alignment.Center
                        ) {
                            val initial = authInfo.displayName.firstOrNull()?.uppercaseChar()?.toString() ?: "A"
                            Text(
                                text = initial,
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    },
                    error = {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(ClaudeTerracotta),
                            contentAlignment = Alignment.Center
                        ) {
                            val initial = authInfo.displayName.firstOrNull()?.uppercaseChar()?.toString() ?: "A"
                            Text(
                                text = initial,
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Display Name
                Text(
                    text = authInfo.displayName,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { heading() }
                )

                if (authInfo.email.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = authInfo.email,
                        fontSize = 12.5.sp,
                        color = mutedText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // User Tier / Plan Badge Card
                val tierName = authInfo.userTier.ifBlank { authInfo.planName.ifBlank { L.drawer.profileDetail.pro } }
                if (tierName.isNotBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = badgeBg,
                        border = androidx.compose.foundation.BorderStroke(1.dp, ClaudeTerracotta.copy(alpha = 0.35f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.WorkspacePremium,
                                contentDescription = null,
                                tint = accessibleBadgeText,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = tierName,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = accessibleBadgeText
                            )
                            if (authInfo.planName.isNotBlank() && authInfo.planName != tierName) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "•  ${authInfo.planName}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = accessibleBadgeText
                                )
                            }
                        }
                    }
                }

                // Credits Section (if prompt or flow credits available)
                if (authInfo.availablePromptCredits != null || authInfo.availableFlowCredits != null) {
                    Spacer(modifier = Modifier.height(14.dp))
                    val creditsBg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                    val creditsMuted = accessibleMutedColor(alpha = 0.72f, background = creditsBg, surfaceFallback = surfaceBg)
                    val creditsQuotaGreen = ensureAccessibleContrast(QuotaGreen, creditsBg, 3.0f, surfaceBg)
                    val creditsTerracotta = ensureAccessibleContrast(ClaudeTerracotta, creditsBg, 3.0f, surfaceBg)
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = creditsBg,
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (authInfo.availablePromptCredits != null) {
                                Column(
                                    modifier = Modifier.semantics(mergeDescendants = true) {},
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Outlined.Token,
                                            contentDescription = null,
                                            tint = creditsQuotaGreen,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = L.drawer.profileDetail.promptCredits,
                                            fontSize = 11.sp,
                                            color = creditsMuted
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = L.drawer.profileDetail.availablepromptcreditsAvailable(authInfo.availablePromptCredits),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }

                            if (authInfo.availablePromptCredits != null && authInfo.availableFlowCredits != null) {
                                Box(
                                    modifier = Modifier
                                        .height(26.dp)
                                        .width(1.dp)
                                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                )
                            }

                            if (authInfo.availableFlowCredits != null) {
                                Column(
                                    modifier = Modifier.semantics(mergeDescendants = true) {},
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Outlined.Speed,
                                            contentDescription = null,
                                            tint = creditsTerracotta,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = L.drawer.profileDetail.flowCredits,
                                            fontSize = 11.sp,
                                            color = creditsMuted
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = L.drawer.profileDetail.availableflowcreditsAvailable(authInfo.availableFlowCredits),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                Spacer(modifier = Modifier.height(12.dp))

                // Details: Email
                if (authInfo.email.isNotBlank()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics(mergeDescendants = true) {}
                            .padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Email,
                            contentDescription = null,
                            tint = mutedIcon,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = L.drawer.profileDetail.accountEmail,
                                fontSize = 11.sp,
                                color = mutedText
                            )
                            Text(
                                text = authInfo.email,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Normal,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Details: Auth Status
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics(mergeDescendants = true) {}
                        .padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.VerifiedUser,
                        contentDescription = null,
                        tint = accessibleTerracottaIcon,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = L.drawer.profileDetail.status,
                            fontSize = 11.sp,
                            color = mutedText
                        )
                        Text(
                            text = if (authInfo.isOffline) L.drawer.profileDetail.connectedOfflineVerification else L.drawer.profileDetail.activeAuthenticatedAgyRpc,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Details: Upgrade URI if available
                if (authInfo.upgradeSubscriptionUri.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .minTouchTarget(48.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(role = Role.Button) {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(authInfo.upgradeSubscriptionUri))
                                    context.startActivity(intent)
                                } catch (_: Exception) {}
                            }
                            .semantics(mergeDescendants = true) {}
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                            contentDescription = null,
                            tint = accessibleTerracottaIcon,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = L.drawer.profileDetail.subscription,
                                fontSize = 11.sp,
                                color = mutedText
                            )
                            Text(
                                text = if (authInfo.upgradeSubscriptionText.isNotBlank()) authInfo.upgradeSubscriptionText else L.drawer.profileDetail.manageUpgradePlan,
                                fontSize = 12.sp,
                                color = accessibleTerracottaText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Logout Button
                OutlinedButton(
                    onClick = { confirmSignOut = true },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = accessibleError
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, accessibleError.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .minTouchTarget(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.Logout,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = L.drawer.profileDetail.signOut,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
