package ai.antigem.ui.run

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ai.antigem.data.daemon.IdeDaemonManager
import ai.antigem.data.ide.RunConfigManager
import ai.antigem.data.ide.RunConfiguration
import ai.antigem.data.preferences.AuthPreferences
import ai.antigem.data.run.RunEntry
import ai.antigem.data.run.RunManager
import ai.antigem.data.run.RunPanel
import ai.antigem.data.run.RunStatus
import ai.antigem.data.run.RunTemplate
import ai.antigem.data.run.RunTemplateGroup
import ai.antigem.data.run.RunTemplates
import ai.antigem.theme.AppColors
import ai.antigem.theme.ClaudeTerracotta
import ai.antigem.ui.components.LocalScreenAccessible
import ai.antigem.ui.components.WorkspaceFolderBrowserDialog
import ai.antigem.ui.components.accessibleMutedColor
import ai.antigem.ui.components.ensureAccessibleContrast
import ai.antigem.ui.components.minTouchTarget
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import kotlin.math.roundToInt
import ai.antigem.i18n.L

/** The last folder the Run panel worked in, and the recent ones, kept across app starts. */
private class RunPanelPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("antigem_run_panel", Context.MODE_PRIVATE)
    var folder: String?
        get() = prefs.getString("folder", null)
        set(value) { prefs.edit().putString("folder", value).apply() }
    val recent: List<String>
        get() = runCatching { JSONArray(prefs.getString("recent", "[]")).let { a -> List(a.length()) { a.getString(it) } } }.getOrDefault(emptyList())

    fun remember(folder: String) {
        val list = (listOf(folder) + recent.filter { it != folder }).take(8)
        prefs.edit().putString("folder", folder).putString("recent", JSONArray(list).toString()).apply()
    }
}

private enum class CommandFilter(private val labelText: () -> String) { PROJECT({ L.run.screen.project }), GLOBAL({ L.common.global }), SUGGESTED({ L.run.screen.suggested }) ;
    val label: String get() = labelText()
}

/**
 * The Run screen, one of the app's screens like Chat, IDE and Terminal (floating switcher → Run): a folder's commands,
 * and the live terminal of each run. Runs keep going when another screen is in front. [onClose] goes back to the
 * previous screen; [onSendToChat] switches to the chat after a run's output was put in its message box.
 */
@Composable
fun RunScreen(isVisible: Boolean, onClose: () -> Unit, onSendToChat: () -> Unit) {
    val runs by RunManager.runs.collectAsState()
    BackHandler(enabled = isVisible) { onClose() }
    // results that finish while the screen is in front are seen already (no bubble for them later)
    LaunchedEffect(isVisible, runs) { if (isVisible) RunPanel.markSeen() }
    RunScreenContent(runs = runs, isVisible = isVisible, onClose = onClose, onSendToChat = onSendToChat)
}

/**
 * Above every screen: while a command runs the screen stays on, and while another screen is in front a bubble shows
 * the runs (spinner, then ✓ / ✗ until looked at); tapping it opens the Run screen.
 */
@Composable
fun RunBubbleHost(isRunScreen: Boolean) {
    val runs by RunManager.runs.collectAsState()
    val seen by RunPanel.seen.collectAsState()
    val screenAccessible = LocalScreenAccessible.current
    val anyRunning = runs.any { it.status == RunStatus.RUNNING }
    // keepScreenOn on any view of the window keeps it on, without touching the flag other screens set
    androidx.compose.ui.viewinterop.AndroidView(
        factory = { android.view.View(it).apply {
            isFocusable = false
            importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        } },
        update = {
            it.keepScreenOn = anyRunning
            it.isFocusable = false
            it.importantForAccessibility = if (screenAccessible) {
                android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            } else {
                android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            }
        },
        modifier = Modifier.size(1.dp).clearAndSetSemantics { hideFromAccessibility() }
    )
    val showBubble = !isRunScreen && runs.any { it.status == RunStatus.RUNNING || RunPanel.key(it) !in seen }
    if (showBubble) RunBubble(runs, seen)
}

@Composable
private fun RunScreenContent(runs: List<RunEntry>, isVisible: Boolean, onClose: () -> Unit, onSendToChat: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { RunPanelPrefs(context) }
    val activeProject by IdeDaemonManager.activeProject.collectAsState()
    val ideProjects by IdeDaemonManager.projects.collectAsState()

    var folder by remember { mutableStateOf(prefs.folder ?: activeProject?.path ?: "") }
    var filter by remember { mutableStateOf(CommandFilter.PROJECT) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var projectConfigs by remember { mutableStateOf<List<RunConfiguration>>(emptyList()) }
    var projectFileBroken by remember { mutableStateOf(false) }
    var globalConfigs by remember { mutableStateOf<List<RunConfiguration>>(emptyList()) }
    var suggested by remember { mutableStateOf<List<RunTemplateGroup>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<CommandEdit?>(null) }
    var showBrowser by remember { mutableStateOf(false) }
    val selectedRunId by RunPanel.selectedRunId.collectAsState()
    val selectedRun = runs.find { it.id == selectedRunId }
    var showRuns by remember { mutableStateOf(selectedRun != null) }
    LaunchedEffect(selectedRunId) { if (selectedRunId != null) showRuns = true }

    // A PC that doesn't give the phone a shell: its folders can't be run in from here
    val cannotRun = AuthPreferences.currentRemoteMode && !AuthPreferences.remoteShell

    LaunchedEffect(folder, refreshKey) {
        if (folder.isBlank()) {
            projectConfigs = emptyList(); suggested = emptyList()
        } else {
            loading = true
            val loaded = RunConfigManager.loadProject(folder)
            projectFileBroken = loaded == null
            projectConfigs = loaded.orEmpty()
            suggested = RunTemplates.detect(context, folder)
            loading = false
        }
        globalConfigs = RunConfigManager.loadGlobal(context)
    }
    // Opening a folder with nothing saved yet: show what can be run in it
    LaunchedEffect(folder, loading) {
        if (!loading && projectConfigs.isEmpty() && suggested.isNotEmpty() && filter == CommandFilter.PROJECT) filter = CommandFilter.SUGGESTED
    }

    fun pickFolder(path: String) {
        folder = path.trimEnd('/').ifBlank { path }
        prefs.remember(folder)
    }
    val requestedFolder by RunPanel.requestedFolder.collectAsState()
    LaunchedEffect(requestedFolder) {
        requestedFolder?.let { pickFolder(it); RunPanel.requestedFolder.value = null }
    }

    fun start(name: String, command: String, cwd: String?, env: Map<String, String>? = null, args: List<String>? = null) {
        if (cannotRun) {
            ai.antigem.ui.components.AppToastHelper.showToast(
                L.run.screen.thisPcDoesntLetThePhone, ai.antigem.ui.components.ChatToastType.ERROR
            )
            return
        }
        val resolved = RunConfigManager.resolveVariables(command, null, folder)
        val withArgs = if (args.isNullOrEmpty()) resolved else resolved + " " + args.joinToString(" ") { shellQuote(it) }
        val exports = env.orEmpty().entries.joinToString("") { (k, v) -> "export $k=${shellQuote(v)}\n" }
        val run = RunManager.start(context, name, exports + withArgs, resolveCwd(cwd, folder))
        if (folder.isNotBlank()) prefs.remember(folder)
        RunPanel.selectedRunId.value = run.id
        showRuns = true
    }

    fun saveCommand(edit: CommandEdit, config: RunConfiguration, global: Boolean) {
        scope.launch {
            val ok = if (global) {
                val list = globalConfigs.filterNot { it.name.equals(edit.originalName ?: config.name, ignoreCase = true) || it.name.equals(config.name, ignoreCase = true) }
                RunConfigManager.saveGlobal(context, list + config)
            } else {
                val list = projectConfigs.filterNot { it.name.equals(edit.originalName ?: config.name, ignoreCase = true) || it.name.equals(config.name, ignoreCase = true) }
                RunConfigManager.saveProject(folder, list + config)
            }
            // moved between Project and Global: drop it from where it was
            if (ok && edit.originalName != null && edit.wasGlobal != global) {
                if (edit.wasGlobal) RunConfigManager.saveGlobal(context, globalConfigs.filterNot { it.name == edit.originalName })
                else RunConfigManager.saveProject(folder, projectConfigs.filterNot { it.name == edit.originalName })
            }
            if (!ok) ai.antigem.ui.components.AppToastHelper.showToast(L.run.screen.couldntSaveTheCommand, ai.antigem.ui.components.ChatToastType.ERROR)
            filter = if (global) CommandFilter.GLOBAL else CommandFilter.PROJECT
            refreshKey++
        }
    }

    fun deleteCommand(config: RunConfiguration, global: Boolean) {
        scope.launch {
            if (global) RunConfigManager.saveGlobal(context, globalConfigs.filterNot { it.name == config.name })
            else RunConfigManager.saveProject(folder, projectConfigs.filterNot { it.name == config.name })
            refreshKey++
        }
    }

    Surface(color = AppColors.editorBg, modifier = Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                PanelHeader(
                    onClose = onClose,
                    showRuns = showRuns,
                    runCount = runs.size,
                    runningCount = runs.count { it.status == RunStatus.RUNNING },
                    onShowCommands = { showRuns = false },
                    onShowRuns = {
                        showRuns = true
                        if (selectedRun == null) RunPanel.selectedRunId.value = runs.lastOrNull()?.id
                    }
                )
                if (showRuns) {
                    RunsView(
                        runs = runs,
                        selected = selectedRun ?: runs.lastOrNull(),
                        isVisible = isVisible,
                        onSelect = { RunPanel.selectedRunId.value = it },
                        onSendToChat = { run ->
                            RunPanel.chatHandoff.value = chatText(run, RunManager.recentOutput(run.id))
                            onSendToChat()
                        },
                        onBackToCommands = { showRuns = false }
                    )
                } else {
                    CommandsView(
                        folder = folder,
                        activeProjectPath = activeProject?.path,
                        ideProjects = ideProjects.map { it.path },
                        recent = prefs.recent,
                        filter = filter,
                        onFilter = { filter = it },
                        loading = loading,
                        cannotRun = cannotRun,
                        projectConfigs = projectConfigs,
                        projectFileBroken = projectFileBroken,
                        globalConfigs = globalConfigs,
                        suggested = suggested,
                        onPickFolder = ::pickFolder,
                        onBrowse = { showBrowser = true },
                        onRunConfig = { start(it.name, it.command.orEmpty(), it.cwd, it.env, it.args) },
                        onRunTemplate = { start(it.name, it.command, it.cwd) },
                        onAddTemplate = { t ->
                            scope.launch {
                                val list = projectConfigs.filterNot { it.name.equals(t.name, ignoreCase = true) }
                                if (RunConfigManager.saveProject(folder, list + t.toConfig())) {
                                    ai.antigem.ui.components.AppToastHelper.showToast(L.run.screen.addedNameToTheProject(t.name))
                                }
                                refreshKey++
                            }
                        },
                        onNew = { editing = CommandEdit(global = filter == CommandFilter.GLOBAL || folder.isBlank()) },
                        onEdit = { cfg, global -> editing = CommandEdit(cfg, cfg.name, global, global) },
                        onDuplicate = { cfg, global -> editing = CommandEdit(cfg.copy(name = cfg.name + " copy"), null, global, global) },
                        onDelete = ::deleteCommand
                    )
                }
            }
    }

    editing?.let { edit ->
        CommandEditorDialog(
            edit = edit,
            canSaveToProject = folder.isNotBlank(),
            onDismiss = { editing = null },
            onSave = { cfg, global -> editing = null; saveCommand(edit, cfg, global) }
        )
    }
    if (showBrowser) {
        WorkspaceFolderBrowserDialog(
            initialPath = folder.ifBlank { null },
            onSelectFolder = { pickFolder(it.path); showBrowser = false },
            onDismiss = { showBrowser = false }
        )
    }
}

@Composable
private fun PanelHeader(
    onClose: () -> Unit,
    showRuns: Boolean,
    runCount: Int,
    runningCount: Int,
    onShowCommands: () -> Unit,
    onShowRuns: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(AppColors.editorPanel)
            .statusBarsPadding()
            .padding(start = 4.dp, end = 10.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onClose, modifier = Modifier.minTouchTarget(48.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, L.common.back, tint = AppColors.editorText)
        }
        Box(
            Modifier.size(32.dp).clip(RoundedCornerShape(9.dp)).background(ClaudeTerracotta.copy(alpha = 0.15f)).clearAndSetSemantics {},
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Filled.PlayArrow, null, tint = ClaudeTerracotta, modifier = Modifier.size(20.dp)) }
        Spacer(Modifier.width(8.dp))
        Text(L.common.run, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = AppColors.editorText, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.width(12.dp))
        Row(
            Modifier.clip(RoundedCornerShape(10.dp)).background(AppColors.editorBg).padding(3.dp).selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            SegmentButton(L.run.screen.commands, selected = !showRuns, onClick = onShowCommands)
            SegmentButton(
                if (runningCount > 0) L.run.screen.runsRunningcountRunning(runningCount) else "Runs ($runCount)",
                selected = showRuns,
                enabled = runCount > 0,
                onClick = onShowRuns
            )
        }
    }
    HorizontalDivider(color = AppColors.editorBorder, thickness = 0.5.dp)
}

@Composable
private fun SegmentButton(label: String, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    val bg = if (selected) ClaudeTerracotta.copy(alpha = 0.18f) else Color.Transparent
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(8.dp),
        color = bg,
        modifier = Modifier.minTouchTarget(48.dp).semantics {
            role = Role.Tab
            this.selected = selected
        }
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                label,
                fontSize = 12.5.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = when {
                    selected -> ensureAccessibleContrast(ClaudeTerracotta, bg, 4.5f, AppColors.editorBg)
                    enabled -> AppColors.editorText
                    else -> ensureAccessibleContrast(AppColors.editorMuted, AppColors.editorBg, 4.5f)
                },
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }
    }
}

// ---------------------------------------------------------------- commands

@Composable
private fun CommandsView(
    folder: String,
    activeProjectPath: String?,
    ideProjects: List<String>,
    recent: List<String>,
    filter: CommandFilter,
    onFilter: (CommandFilter) -> Unit,
    loading: Boolean,
    cannotRun: Boolean,
    projectConfigs: List<RunConfiguration>,
    projectFileBroken: Boolean,
    globalConfigs: List<RunConfiguration>,
    suggested: List<RunTemplateGroup>,
    onPickFolder: (String) -> Unit,
    onBrowse: () -> Unit,
    onRunConfig: (RunConfiguration) -> Unit,
    onRunTemplate: (RunTemplate) -> Unit,
    onAddTemplate: (RunTemplate) -> Unit,
    onNew: () -> Unit,
    onEdit: (RunConfiguration, Boolean) -> Unit,
    onDuplicate: (RunConfiguration, Boolean) -> Unit,
    onDelete: (RunConfiguration, Boolean) -> Unit
) {
    // Commands written for the IDE's open file (${file}…) mean nothing here
    val usableProject = projectConfigs.filterNot { isIdeOnly(it) }
    val hiddenIdeOnly = projectConfigs.size - usableProject.size
    val suggestedCount = suggested.sumOf { it.templates.size }

    Column(Modifier.fillMaxSize().navigationBarsPadding().imePadding()) {
        FolderRow(folder, activeProjectPath, ideProjects, recent, onPickFolder, onBrowse)

        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).selectableGroup().padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CommandFilter.entries.forEach { f ->
                val count = when (f) {
                    CommandFilter.PROJECT -> usableProject.size
                    CommandFilter.GLOBAL -> globalConfigs.size
                    CommandFilter.SUGGESTED -> suggestedCount
                }
                FilterPill("${f.label} ($count)", selected = filter == f) { onFilter(f) }
            }
        }

        if (cannotRun) {
            Banner(L.run.screen.thisPcDoesntLetThePhone2, AppColors.warning)
        }

        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (loading) {
                item { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp, color = ClaudeTerracotta) } }
            }
            when (filter) {
                CommandFilter.PROJECT -> {
                    if (folder.isBlank()) {
                        item { EmptyHint(L.run.screen.pickAFolderAboveItsCommands) }
                    } else {
                        if (projectFileBroken) item { Banner(".antigem/run.json in this folder isn't valid JSON. Fix it (or delete it) to see its commands.", AppColors.danger) }
                        if (usableProject.isEmpty() && !loading && !projectFileBroken) {
                            item {
                                EmptyHint(
                                    if (suggestedCount > 0) "No commands saved for this folder yet. Add your own, or pick one from Suggested ($suggestedCount)."
                                    else L.run.screen.noCommandsSavedForThisFolder
                                )
                            }
                        }
                        itemsIndexed(usableProject, key = { i, c -> "p:$i:" + c.name }) { _, cfg ->
                            CommandRow(cfg.name, cfg.command.orEmpty(), cfg.cwd, onRun = { onRunConfig(cfg) },
                                onEdit = { onEdit(cfg, false) }, onDuplicate = { onDuplicate(cfg, false) }, onDelete = { onDelete(cfg, false) })
                        }
                        if (hiddenIdeOnly > 0) {
                            item {
                                Text(
                                    "$hiddenIdeOnly IDE-only command(s) hidden (they use the IDE's open file).",
                                    fontSize = 11.5.sp,
                                    color = ensureAccessibleContrast(AppColors.editorMuted, AppColors.editorBg, 4.5f),
                                    modifier = Modifier.padding(4.dp)
                                )
                            }
                        }
                    }
                }
                CommandFilter.GLOBAL -> {
                    if (globalConfigs.isEmpty()) {
                        item { EmptyHint(L.run.screen.globalCommandsWorkInAnyFolder) }
                    }
                    itemsIndexed(globalConfigs, key = { i, c -> "g:$i:" + c.name }) { _, cfg ->
                        CommandRow(cfg.name, cfg.command.orEmpty(), cfg.cwd, onRun = { onRunConfig(cfg) },
                            onEdit = { onEdit(cfg, true) }, onDuplicate = { onDuplicate(cfg, true) }, onDelete = { onDelete(cfg, true) })
                    }
                }
                CommandFilter.SUGGESTED -> {
                    if (suggested.isEmpty() && !loading) {
                        item { EmptyHint(if (folder.isBlank()) L.run.screen.pickAFolderToSeeWhat else L.run.screen.nothingRecognisedInThisFolderFlutter) }
                    }
                    suggested.forEach { group ->
                        item(key = "h:" + group.kind) {
                            Text(
                                group.kind,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = ensureAccessibleContrast(ClaudeTerracotta, AppColors.editorBg, 4.5f),
                                modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 2.dp).semantics { heading() }
                            )
                        }
                        if (group.needsAndroidSetup) {
                            item(key = "w:" + group.kind) {
                                Banner(L.run.screen.theseNeedTheAndroidBuildTools, AppColors.warning)
                            }
                        }
                        itemsIndexed(group.templates, key = { i, t -> "s:" + group.kind + ":$i" }) { _, t ->
                            TemplateRow(t, onRun = { onRunTemplate(t) }, onAdd = if (folder.isNotBlank()) ({ onAddTemplate(t) }) else null)
                        }
                    }
                }
            }
        }

        HorizontalDivider(color = AppColors.editorBorder, thickness = 0.5.dp)
        Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.End) {
            Button(onClick = onNew, colors = ButtonDefaults.buttonColors(containerColor = ClaudeTerracotta), modifier = Modifier.minTouchTarget(48.dp)) {
                Icon(Icons.Filled.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(L.run.screen.newCommand)
            }
        }
    }
}

@Composable
private fun FolderRow(
    folder: String,
    activeProjectPath: String?,
    ideProjects: List<String>,
    recent: List<String>,
    onPick: (String) -> Unit,
    onBrowse: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    val mutedPanel = ensureAccessibleContrast(AppColors.editorMuted, AppColors.editorPanel, 4.5f, AppColors.editorBg)
    Box(Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 4.dp)) {
        Surface(
            onClick = { menu = true },
            shape = RoundedCornerShape(12.dp),
            color = AppColors.editorPanel,
            border = BorderStroke(1.dp, AppColors.editorBorder),
            modifier = Modifier.fillMaxWidth().minTouchTarget(48.dp).semantics(mergeDescendants = true) {}
        ) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.FolderOpen, null, tint = ClaudeTerracotta, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (folder.isBlank()) L.run.screen.chooseAFolder else folder.substringAfterLast('/').ifBlank { folder },
                        fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = AppColors.editorText, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    if (folder.isNotBlank()) {
                        Text(shortPath(folder), fontSize = 11.sp, color = mutedPanel, maxLines = 1, overflow = TextOverflow.Ellipsis, fontFamily = FontFamily.Monospace)
                    }
                }
                Icon(Icons.Filled.UnfoldMore, L.run.screen.changeFolder, tint = mutedPanel, modifier = Modifier.size(20.dp))
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            val seen = mutableSetOf<String>()
            val choices = buildList {
                activeProjectPath?.let { add(it to L.run.screen.ideProject) }
                recent.forEach { add(it to L.run.screen.recent) }
                ideProjects.forEach { add(it to L.run.screen.project) }
            }.filter { seen.add(it.first.trimEnd('/')) }.take(14)
            choices.forEach { (path, label) ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(path.substringAfterLast('/').ifBlank { path }, fontSize = 13.5.sp, fontWeight = if (path == folder) FontWeight.Bold else FontWeight.Normal)
                            Text("$label · ${shortPath(path)}", fontSize = 11.sp, color = accessibleMutedColor(alpha = 0.72f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    },
                    onClick = { menu = false; onPick(path) }
                )
            }
            if (choices.isNotEmpty()) HorizontalDivider()
            DropdownMenuItem(
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                text = { Text(L.run.screen.browse) },
                onClick = { menu = false; onBrowse() }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CommandRow(
    name: String,
    command: String,
    cwd: String?,
    onRun: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val mutedPanel = ensureAccessibleContrast(AppColors.editorMuted, AppColors.editorPanel, 4.5f, AppColors.editorBg)
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = AppColors.editorPanel,
        border = BorderStroke(0.5.dp, AppColors.editorBorder),
        modifier = Modifier.fillMaxWidth().minTouchTarget(48.dp).clip(RoundedCornerShape(12.dp)).combinedClickable(onClick = onRun, onLongClick = { menu = true })
    ) {
        Row(Modifier.padding(start = 6.dp, end = 2.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onRun, modifier = Modifier.minTouchTarget(48.dp)) {
                Box(Modifier.size(32.dp).clip(CircleShape).background(ClaudeTerracotta.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.PlayArrow, L.run.screen.runName(name), tint = ClaudeTerracotta, modifier = Modifier.size(20.dp))
                }
            }
            Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
                Text(name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = AppColors.editorText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(command, fontSize = 11.5.sp, fontFamily = FontFamily.Monospace, color = mutedPanel, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (!cwd.isNullOrBlank() && cwd != "\${workspaceFolder}") {
                    Text(L.run.screen.inCwd(cwd), fontSize = 10.5.sp, color = mutedPanel, maxLines = 1)
                }
            }
            Box {
                IconButton(onClick = { menu = true }, modifier = Modifier.minTouchTarget(48.dp)) { Icon(Icons.Filled.MoreVert, L.common.more, tint = mutedPanel) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(leadingIcon = { Icon(Icons.Outlined.Edit, null) }, text = { Text(L.common.edit) }, onClick = { menu = false; onEdit() })
                    DropdownMenuItem(leadingIcon = { Icon(Icons.Outlined.ContentCopy, null) }, text = { Text(L.run.screen.duplicate) }, onClick = { menu = false; onDuplicate() })
                    DropdownMenuItem(leadingIcon = { Icon(Icons.Outlined.Delete, null) }, text = { Text(L.common.delete) }, onClick = { menu = false; confirmDelete = true })
                }
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete \"$name\"?") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text(L.common.delete, color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(L.common.cancel) } }
        )
    }
}

@Composable
private fun TemplateRow(t: RunTemplate, onRun: () -> Unit, onAdd: (() -> Unit)?) {
    val mutedPanel = ensureAccessibleContrast(AppColors.editorMuted, AppColors.editorPanel, 4.5f, AppColors.editorBg)
    Surface(
        onClick = onRun,
        shape = RoundedCornerShape(12.dp),
        color = AppColors.editorPanel,
        border = BorderStroke(0.5.dp, AppColors.editorBorder),
        modifier = Modifier.fillMaxWidth().minTouchTarget(48.dp)
    ) {
        Row(Modifier.padding(start = 6.dp, end = 2.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onRun, modifier = Modifier.minTouchTarget(48.dp)) {
                Box(Modifier.size(32.dp).clip(CircleShape).background(ClaudeTerracotta.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.PlayArrow, L.run.screen.runName(t.name), tint = ClaudeTerracotta, modifier = Modifier.size(20.dp))
                }
            }
            Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
                Text(t.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = AppColors.editorText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    (t.cwd?.let { "cd $it && " } ?: "") + t.command,
                    fontSize = 11.5.sp, fontFamily = FontFamily.Monospace, color = mutedPanel, maxLines = 2, overflow = TextOverflow.Ellipsis
                )
                t.note?.takeIf { it.isNotBlank() }?.let {
                    Text(it, fontSize = 10.5.sp, color = mutedPanel, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            if (onAdd != null) {
                IconButton(onClick = onAdd, modifier = Modifier.minTouchTarget(48.dp)) { Icon(Icons.Outlined.BookmarkAdd, L.run.screen.saveToThisProject, tint = mutedPanel) }
            }
        }
    }
}

@Composable
private fun FilterPill(label: String, selected: Boolean, role: Role = Role.Tab, onClick: () -> Unit) {
    val bg = if (selected) ClaudeTerracotta.copy(alpha = 0.18f) else AppColors.editorPanel
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = bg,
        border = BorderStroke(1.dp, if (selected) ClaudeTerracotta.copy(alpha = 0.6f) else AppColors.editorBorder),
        modifier = Modifier.minTouchTarget(48.dp).semantics {
            this.role = role
            this.selected = selected
        }
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                label, fontSize = 12.5.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) ensureAccessibleContrast(ClaudeTerracotta, bg, 4.5f, AppColors.editorBg) else AppColors.editorText,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
private fun Banner(text: String, color: Color) {
    val textColor = ensureAccessibleContrast(color, AppColors.editorBg, 4.5f)
    Surface(shape = RoundedCornerShape(10.dp), color = color.copy(alpha = 0.12f), border = BorderStroke(1.dp, color.copy(alpha = 0.35f)), modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(text, fontSize = 12.sp, color = textColor, modifier = Modifier.padding(10.dp))
    }
}

@Composable
private fun EmptyHint(text: String) {
    Text(
        text,
        fontSize = 12.5.sp,
        color = ensureAccessibleContrast(AppColors.editorMuted, AppColors.editorBg, 4.5f),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 14.dp)
    )
}

// ---------------------------------------------------------------- editor

private data class CommandEdit(
    val config: RunConfiguration? = null,
    /** Name before editing (null = a new command). */
    val originalName: String? = null,
    val global: Boolean = false,
    val wasGlobal: Boolean = false
)

@Composable
private fun CommandEditorDialog(
    edit: CommandEdit,
    canSaveToProject: Boolean,
    onDismiss: () -> Unit,
    onSave: (RunConfiguration, Boolean) -> Unit
) {
    var name by remember { mutableStateOf(edit.config?.name.orEmpty()) }
    var command by remember { mutableStateOf(edit.config?.command.orEmpty()) }
    var cwd by remember { mutableStateOf(edit.config?.cwd?.takeIf { it != "\${workspaceFolder}" }.orEmpty()) }
    var global by remember { mutableStateOf(edit.global || !canSaveToProject) }
    val canSave = name.isNotBlank() && command.isNotBlank()

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = AppColors.editorBg,
            border = BorderStroke(1.dp, AppColors.editorBorder),
            modifier = Modifier.fillMaxWidth(0.94f).imePadding()
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val mutedBg = ensureAccessibleContrast(AppColors.editorMuted, AppColors.editorBg, 4.5f)
                Text(
                    if (edit.originalName == null) L.run.screen.newCommand else L.run.screen.editCommand,
                    fontSize = 16.sp, fontWeight = FontWeight.Bold, color = AppColors.editorText,
                    modifier = Modifier.semantics { heading() }
                )
                OutlinedTextField(
                    value = name, onValueChange = { name = it }, singleLine = true,
                    label = { Text(L.common.name) }, placeholder = { Text(L.run.screen.buildDebugApk) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = command, onValueChange = { command = it },
                    label = { Text(L.common.command) }, placeholder = { Text(L.run.screen.adevGradlewAssembledebug) },
                    textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
                    minLines = 2, maxLines = 8,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = cwd, onValueChange = { cwd = it }, singleLine = true,
                    label = { Text(L.run.screen.folderOptional) }, placeholder = { Text(L.run.screen.androidEmptyTheProjectFolder) },
                    textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
                    modifier = Modifier.fillMaxWidth()
                )
                Text(L.run.screen.saveTo, fontSize = 12.sp, color = mutedBg)
                Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterPill(L.run.screen.thisProject, selected = !global, role = Role.RadioButton) { if (canSaveToProject) global = false }
                    FilterPill(L.run.screen.globalAnyFolder, selected = global, role = Role.RadioButton) { global = true }
                }
                Text(
                    if (global) "Kept in the app's home (~/.antigem/global-run.json); runs in the folder picked in the panel."
                    else L.run.screen.savedInTheProjectsAntigemRun,
                    fontSize = 11.5.sp, color = mutedBg
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss, modifier = Modifier.minTouchTarget(48.dp)) { Text(L.common.cancel) }
                    Spacer(Modifier.width(6.dp))
                    Button(
                        enabled = canSave,
                        colors = ButtonDefaults.buttonColors(containerColor = ClaudeTerracotta),
                        modifier = Modifier.minTouchTarget(48.dp),
                        onClick = {
                            val base = edit.config ?: RunConfiguration(name = name.trim())
                            onSave(base.copy(name = name.trim(), type = "terminal", command = command.trim(), cwd = cwd.trim().ifBlank { null }), global)
                        }
                    ) { Text(L.common.save) }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- runs

@Composable
private fun RunsView(
    runs: List<RunEntry>,
    selected: RunEntry?,
    isVisible: Boolean,
    onSelect: (String) -> Unit,
    onSendToChat: (RunEntry) -> Unit,
    onBackToCommands: () -> Unit
) {
    val context = LocalContext.current
    val screenAccessible = LocalScreenAccessible.current
    val mutedPanel = ensureAccessibleContrast(AppColors.editorMuted, AppColors.editorPanel, 4.5f, AppColors.editorBg)
    if (selected == null) {
        Box(Modifier.fillMaxSize().navigationBarsPadding(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(L.run.screen.nothingRunning, color = ensureAccessibleContrast(AppColors.editorMuted, AppColors.editorBg, 4.5f))
                TextButton(onClick = onBackToCommands, modifier = Modifier.minTouchTarget(48.dp)) { Text(L.run.screen.showCommands) }
            }
        }
        return
    }
    Column(Modifier.fillMaxSize()) {
        // one tab per run (Metro and a build side by side)
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).selectableGroup().padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            runs.forEach { run ->
                val isSel = run.id == selected.id
                Surface(
                    onClick = { onSelect(run.id) },
                    shape = RoundedCornerShape(10.dp),
                    color = if (isSel) AppColors.editorPanel else Color.Transparent,
                    border = BorderStroke(1.dp, if (isSel) ClaudeTerracotta.copy(alpha = 0.55f) else AppColors.editorBorder),
                    modifier = Modifier.minTouchTarget(48.dp).semantics {
                        role = Role.Tab
                        this.selected = isSel
                    }
                ) {
                    Row(Modifier.padding(start = 10.dp, end = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        StatusDot(run.status, Modifier.size(14.dp))
                        Spacer(Modifier.width(7.dp))
                        Text(run.name, fontSize = 12.5.sp, color = AppColors.editorText, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 160.dp))
                        IconButton(onClick = { RunManager.remove(run.id) }, modifier = Modifier.minTouchTarget(48.dp)) {
                            Icon(Icons.Filled.Close, if (run.status == RunStatus.RUNNING) L.run.screen.stopAndClose else L.common.close, tint = mutedPanel, modifier = Modifier.size(15.dp))
                        }
                    }
                }
            }
        }

        // the selected run: what, where, how it went
        Row(
            Modifier.fillMaxWidth().background(AppColors.editorPanel).padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(selected.command.lineSequence().lastOrNull { it.isNotBlank() && !it.startsWith("export ") } ?: selected.command,
                    fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = AppColors.editorText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    statusLine(selected),
                    fontSize = 11.sp,
                    color = ensureAccessibleContrast(statusColor(selected.status), AppColors.editorPanel, 4.5f, AppColors.editorBg),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                )
            }
            if (selected.status == RunStatus.RUNNING) {
                TextButton(onClick = { RunManager.stop(selected.id) }, modifier = Modifier.minTouchTarget(48.dp)) {
                    Icon(Icons.Filled.Stop, null, tint = AppColors.danger, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text(L.common.stop, color = ensureAccessibleContrast(AppColors.danger, AppColors.editorPanel, 4.5f, AppColors.editorBg))
                }
            } else {
                IconButton(onClick = { onSendToChat(selected) }, modifier = Modifier.minTouchTarget(48.dp)) {
                    Icon(
                        Icons.Outlined.ForwardToInbox,
                        L.run.screen.sendOutputToChat,
                        tint = if (selected.status == RunStatus.FAILED) ensureAccessibleContrast(ClaudeTerracotta, AppColors.editorPanel, 4.5f, AppColors.editorBg) else mutedPanel
                    )
                }
            }
            IconButton(onClick = { RunManager.restart(context, selected.id) }, modifier = Modifier.minTouchTarget(48.dp)) {
                Icon(Icons.Filled.Refresh, L.run.screen.runAgain, tint = AppColors.editorText)
            }
        }
        if (selected.status == RunStatus.FAILED) {
            Surface(
                onClick = { onSendToChat(selected) },
                color = AppColors.danger.copy(alpha = 0.12f),
                modifier = Modifier.fillMaxWidth().minTouchTarget(48.dp)
            ) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    val dangerText = ensureAccessibleContrast(AppColors.danger, AppColors.editorBg, 4.5f)
                    Icon(Icons.Outlined.ForwardToInbox, null, tint = dangerText, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(L.run.screen.failedSendTheOutputToThe, fontSize = 12.sp, color = dangerText)
                }
            }
        }

        // the Terminal screen's own terminal (font size, cursor, colors, the two key rows); Enter after the end closes the tab
        Box(Modifier.weight(1f).fillMaxWidth().semantics { contentDescription = L.run.screen.commandOutput }) {
            key(selected.id, selected.startedAt) {
                ai.antigem.ui.components.LocalTerminalContent(
                    onClose = { RunManager.remove(selected.id) },
                    customSession = selected.session,
                    showFloatingPill = false,
                    showSessionHeader = false,
                    focusScreenMode = null,
                    showKeyboardOnOpen = false,
                    isVisible = isVisible && screenAccessible
                )
            }
        }
    }
}

@Composable
private fun StatusDot(status: RunStatus, modifier: Modifier = Modifier) {
    when (status) {
        RunStatus.RUNNING -> CircularProgressIndicator(
            modifier.semantics { contentDescription = L.common.running },
            strokeWidth = 2.dp,
            color = ClaudeTerracotta
        )
        RunStatus.SUCCEEDED -> Icon(Icons.Filled.CheckCircle, L.common.done, tint = AppColors.success, modifier = modifier)
        RunStatus.FAILED -> Icon(Icons.Filled.Error, L.common.failed, tint = AppColors.danger, modifier = modifier)
        RunStatus.STOPPED -> Icon(Icons.Filled.StopCircle, L.common.stopped, tint = AppColors.editorMuted, modifier = modifier)
    }
}

@Composable
private fun statusLine(run: RunEntry): String {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    if (run.status == RunStatus.RUNNING) {
        LaunchedEffect(run.id, run.startedAt) { while (true) { now = System.currentTimeMillis(); delay(1000) } }
    }
    val took = duration(((run.finishedAt ?: now) - run.startedAt).coerceAtLeast(0))
    val where = shortPath(run.folder)
    return when (run.status) {
        RunStatus.RUNNING -> "Running · $took · $where"
        RunStatus.SUCCEEDED -> L.run.screen.doneInTookExit0Where(took, where)
        RunStatus.FAILED -> L.run.screen.failedAfterTookValueWhere(took, (run.exitCode?.let { " · exit $it" } ?: ""), where)
        RunStatus.STOPPED -> L.run.screen.stoppedAfterTookWhere(took, where)
    }
}

private fun statusColor(status: RunStatus): Color = when (status) {
    RunStatus.RUNNING -> ClaudeTerracotta
    RunStatus.SUCCEEDED -> AppColors.success
    RunStatus.FAILED -> AppColors.danger
    RunStatus.STOPPED -> AppColors.editorMuted
}

// ---------------------------------------------------------------- bubble

/** Shown while the panel is hidden: a spinner while something runs, then ✓ / ✗ until the result is looked at. */
@Composable
private fun RunBubble(runs: List<RunEntry>, seen: Set<String>) {
    val density = LocalDensity.current
    var offset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    val running = runs.count { it.status == RunStatus.RUNNING }
    val latestUnseen = runs.lastOrNull { it.status != RunStatus.RUNNING && RunPanel.key(it) !in seen }
    val target = runs.lastOrNull { it.status == RunStatus.RUNNING } ?: latestUnseen ?: runs.lastOrNull()
    val failed = running == 0 && runs.any { it.status == RunStatus.FAILED && RunPanel.key(it) !in seen }
    val ring = when {
        running > 0 -> ClaudeTerracotta
        failed -> AppColors.danger
        else -> AppColors.success
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val sizePx = with(density) { 56.dp.toPx() }
        val maxX = constraints.maxWidth - sizePx
        val maxY = constraints.maxHeight - sizePx
        val startX = with(density) { 12.dp.toPx() }
        val startY = constraints.maxHeight * 0.62f
        Box(
            Modifier
                .offset { IntOffset((startX + offset.x).coerceIn(0f, maxX).roundToInt(), (startY + offset.y).coerceIn(0f, maxY).roundToInt()) }
                .size(56.dp)
                .pointerInput(Unit) {
                    detectDragGestures { change, drag -> change.consume(); offset += drag }
                }
        ) {
            val bubbleDescription = when {
                running > 0 -> L.run.screen.runsRunningcountRunning(running)
                failed -> L.run.screen.aRunFailed
                else -> L.run.screen.runsFinished
            }
            Surface(
                onClick = { RunPanel.show(target?.id) },
                shape = CircleShape,
                color = AppColors.editorPanel,
                border = BorderStroke(2.dp, ring.copy(alpha = 0.85f)),
                shadowElevation = 10.dp,
                modifier = Modifier.fillMaxSize().semantics(mergeDescendants = true) {
                    role = Role.Button
                    contentDescription = bubbleDescription
                    liveRegion = LiveRegionMode.Polite
                }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    when {
                        running > 0 -> {
                            CircularProgressIndicator(Modifier.size(44.dp).clearAndSetSemantics {}, strokeWidth = 2.5.dp, color = ClaudeTerracotta)
                            Icon(Icons.Filled.PlayArrow, null, tint = ClaudeTerracotta, modifier = Modifier.size(22.dp))
                        }
                        failed -> Icon(Icons.Filled.Close, null, tint = AppColors.danger, modifier = Modifier.size(26.dp))
                        else -> Icon(Icons.Filled.Check, null, tint = AppColors.success, modifier = Modifier.size(26.dp))
                    }
                }
            }
            if (running > 1) {
                Box(
                    Modifier.align(Alignment.TopEnd).size(20.dp).clip(CircleShape).background(ClaudeTerracotta).clearAndSetSemantics {},
                    contentAlignment = Alignment.Center
                ) { Text("$running", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White) }
            }
        }
    }
}

// ---------------------------------------------------------------- helpers

private fun isIdeOnly(cfg: RunConfiguration): Boolean {
    val text = (cfg.command.orEmpty() + " " + cfg.cwd.orEmpty())
    return IDE_FILE_VARIABLE.containsMatchIn(text)
}

// Android's regex engine needs the closing brace escaped
private val IDE_FILE_VARIABLE = Regex("""\$\{file(Basename|BasenameNoExtension|Dirname|Ext)?\}""")

/** A command's folder: the project folder, a sub-folder of it, or an absolute / ~ path. */
private fun resolveCwd(cwd: String?, folder: String): String {
    val c = cwd?.trim().orEmpty()
    if (c.isBlank()) return folder
    val resolved = RunConfigManager.resolveVariables(c, null, folder)
    return when {
        resolved.startsWith("/") || resolved.startsWith("~") -> resolved
        folder.isBlank() -> resolved
        else -> folder.trimEnd('/') + "/" + resolved.removePrefix("./")
    }
}

private fun shellQuote(s: String) = "'" + s.replace("'", "'\\''") + "'"

private fun shortPath(path: String): String =
    path.replace(Regex("^/data/(data|user/0)/[^/]+/files/home"), "~")

private fun duration(ms: Long): String {
    val s = ms / 1000
    return when {
        s < 60 -> "${s}s"
        s < 3600 -> L.run.screen.valueMValue2S(s / 60, s % 60)
        else -> L.run.screen.valueHValue2M(s / 3600, (s % 3600) / 60)
    }
}

/** What "Send to chat" puts in the message box: what ran, where, how it ended, and the end of its output. */
private fun chatText(run: RunEntry, output: String): String {
    val how = when (run.status) {
        RunStatus.FAILED -> "failed" + (run.exitCode?.let { L.run.screen.exitCodeValue(it) } ?: "")
        RunStatus.SUCCEEDED -> L.run.screen.finishedExitCode0
        RunStatus.STOPPED -> L.run.screen.wasStopped
        RunStatus.RUNNING -> L.run.screen.isStillRunning
    }
    val fence = if (output.contains("```")) "````" else "```"
    return buildString {
        append(L.run.screen.theCommandNameHowInValue(run.name, how, shortPath(run.folder).ifBlank { L.run.screen.theHomeFolder }))
        append(fence).append("text\n")
        append("$ ").append(run.command.trim()).append("\n")
        // the run's own "▶ command" header line repeats the line above
        append(output.trim().lines().dropWhile { it.trimStart().startsWith("▶ ") }.joinToString("\n").trim())
        append("\n").append(fence).append("\n")
    }
}
