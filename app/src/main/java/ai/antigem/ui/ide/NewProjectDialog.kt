package ai.antigem.ui.ide

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.antigem.i18n.L
import ai.antigem.ui.components.ensureAccessibleContrast
import ai.antigem.ui.components.minTouchTarget

data class ProjectTemplate(
    val id: String,
    val title: String,
    val description: String,
    val icon: ImageVector
)

val PROJECT_TEMPLATES get() = listOf(
    ProjectTemplate("python", L.ide.newProject.pythonProject, L.ide.newProject.mainPyWithStarterCode, Icons.Default.Code),
    ProjectTemplate("node", L.ide.newProject.nodeJsProject, "Index.js & package.json", Icons.Default.Javascript),
    ProjectTemplate("web", L.ide.newProject.webAppHtmlCssJs, "Index.html, style.css, app.js", Icons.Default.Html),
    ProjectTemplate("cpp", L.ide.newProject.cProject, L.ide.newProject.mainCppWithIostream, Icons.Default.Terminal),
    ProjectTemplate("blank", L.ide.newProject.blankProject, L.ide.newProject.emptyDirectoryWithReadmeMd, Icons.Default.FolderOpen)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewProjectDialog(
    onDismiss: () -> Unit,
    onCreateProject: (name: String, template: String) -> Unit
) {
    var projectName by remember { mutableStateOf("") }
    var selectedTemplate by remember { mutableStateOf("python") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(L.ide.newProject.createNewProject, style = MaterialTheme.typography.titleLarge)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val placeholderColor = ensureAccessibleContrast(
                    foreground = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    background = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    minRatio = 4.5f,
                    surfaceFallback = MaterialTheme.colorScheme.surfaceContainerHigh
                )
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (projectName.isNotBlank()) ai.antigem.theme.ClaudeTerracotta.copy(alpha = 0.6f)
                        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .minTouchTarget(48.dp)
                ) {
                    androidx.compose.foundation.text.BasicTextField(
                        value = projectName,
                        onValueChange = { projectName = it },
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 13.5.sp,
                            lineHeight = 18.sp
                        ),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(ai.antigem.theme.ClaudeTerracotta),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 11.dp),
                        decorationBox = { innerTextField ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (projectName.isEmpty()) {
                                    Text(
                                        text = L.ide.newProject.projectNameEGMyAwesome,
                                        fontSize = 13.5.sp,
                                        lineHeight = 18.sp,
                                        color = placeholderColor
                                    )
                                }
                                innerTextField()
                            }
                        }
                    )
                }

                Text(
                    L.ide.newProject.chooseTemplate,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectableGroup(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    PROJECT_TEMPLATES.forEach { template ->
                        val isSelected = selectedTemplate == template.id
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .minTouchTarget(48.dp)
                                .clickable(role = Role.RadioButton) { selectedTemplate = template.id }
                                .semantics(mergeDescendants = true) { selected = isSelected },
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            },
                            tonalElevation = if (isSelected) 4.dp else 0.dp
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = template.icon,
                                    contentDescription = null,
                                    tint = if (isSelected) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = template.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (isSelected) {
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        }
                                    )
                                    Text(
                                        text = template.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (projectName.isNotBlank()) {
                        onCreateProject(projectName.trim(), selectedTemplate)
                    }
                },
                enabled = projectName.isNotBlank()
            ) {
                Text(L.common.create)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(L.common.cancel)
            }
        }
    )
}
