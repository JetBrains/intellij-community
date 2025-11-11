package com.intellij.python.processOutput.impl

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.SnapshotStateSet
import androidx.compose.ui.util.fastMaxOfOrDefault
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.python.community.execService.impl.ExecLoggerService
import com.intellij.python.community.execService.impl.LoggedProcess
import com.intellij.python.community.execService.impl.LoggedProcessLine
import com.intellij.python.processOutput.impl.ProcessOutputBundle.message
import com.intellij.python.processOutput.impl.ui.components.FilterItem
import com.intellij.python.processOutput.impl.ui.toggle
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.jetbrains.python.NON_INTERACTIVE_ROOT_TRACE_CONTEXT
import com.jetbrains.python.TraceContext
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.runBlocking
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.jewel.foundation.lazy.SelectableLazyListState
import org.jetbrains.jewel.foundation.lazy.tree.Tree
import org.jetbrains.jewel.foundation.lazy.tree.TreeGeneratorScope
import org.jetbrains.jewel.foundation.lazy.tree.TreeState
import org.jetbrains.jewel.foundation.lazy.tree.buildTree

internal object ProcessOutputControllerServiceLimits {
    const val MAX_PROCESSES = 256
}

internal interface ProcessOutputController {
    val selectedProcess: StateFlow<LoggedProcess?>
    val processTreeUiState: TreeUiState
    val processOutputUiState: OutputUiState

    fun collapseAllContexts()
    fun expandAllContexts()
    fun selectProcess(process: LoggedProcess?)
    fun toggleTreeFilter(filter: TreeFilter)
    fun toggleOutputFilter(filter: OutputFilter)
    fun toggleProcessInfo()
    fun toggleProcessOutput()
    fun specifyAdditionalMessageToUser(logId: Int, message: @Nls String)
    fun copyOutputToClipboard(loggedProcess: LoggedProcess)

    @RequiresEdt
    fun tryOpenLogInToolWindow(logId: Int): Boolean
}

@ApiStatus.Internal
data class TreeUiState(
    val filters: Set<TreeFilter>,
    val searchState: TextFieldState,
    val selectableLazyListState: SelectableLazyListState,
    val treeState: TreeState,
    val tree: StateFlow<Tree<TreeNode>>,
)

@ApiStatus.Internal
sealed class TreeFilter : FilterItem {
    object ShowTime : TreeFilter() {
        override val title: String = message("process.output.filters.tree.time")
    }

    object ShowBackgroundProcesses : TreeFilter() {
        override val title: String = message("process.output.filters.tree.backgroundProcesses")
    }
}

@ApiStatus.Internal
sealed interface TreeNode {
    data class Context(
        val traceContext: TraceContext,
    ) : TreeNode

    data class Process(
        val process: LoggedProcess,
    ) : TreeNode
}

@ApiStatus.Internal
sealed class OutputFilter : FilterItem {
    object ShowTags : OutputFilter() {
        override val title: String = message("process.output.filters.output.tags")
    }
}

@ApiStatus.Internal
data class OutputUiState(
    val filters: Set<OutputFilter>,
    val isInfoExpanded: StateFlow<Boolean>,
    val isOutputExpanded: StateFlow<Boolean>,
    val lazyListState: LazyListState,
)

@ApiStatus.Internal
@Service(Service.Level.PROJECT)
class ProcessOutputControllerService(
    private val project: Project,
    private val coroutineScope: CoroutineScope,
) : ProcessOutputController {
    private val loggedProcesses: StateFlow<List<LoggedProcess>> = run {
        var processList = listOf<LoggedProcess>()
        ApplicationManager.getApplication().service<ExecLoggerService>()
            .processes
            .map {
                processList = processList + it

                if (processList.size > ProcessOutputControllerServiceLimits.MAX_PROCESSES) {
                    processList = processList.drop(
                        processList.size - ProcessOutputControllerServiceLimits.MAX_PROCESSES,
                    )
                }

                it.traceContext
                    ?.takeIf { context -> context != NON_INTERACTIVE_ROOT_TRACE_CONTEXT }
                    ?.also { context ->
                        processTreeUiState.treeState.openNodes(context.hierarchy())
                    }

                processList
            }
            .stateIn(
                coroutineScope + Dispatchers.EDT,
                SharingStarted.Eagerly,
                emptyList(),
            )
    }

    private val processTree = MutableStateFlow(buildTree<TreeNode> {})
    private val processTreeFilters: SnapshotStateSet<TreeFilter> = mutableStateSetOf(
        TreeFilter.ShowTime,
    )

    private val processOutputFilters: SnapshotStateSet<OutputFilter> = mutableStateSetOf(
        OutputFilter.ShowTags,
    )
    private val processOutputInfoExpanded = MutableStateFlow(false)
    private val processOutputOutputExpanded = MutableStateFlow(true)

    override val selectedProcess: MutableStateFlow<LoggedProcess?> = MutableStateFlow(null)
    override val processTreeUiState: TreeUiState = run {
        val selectableLazyListState = SelectableLazyListState(LazyListState())
        TreeUiState(
            filters = processTreeFilters,
            searchState = TextFieldState(),
            selectableLazyListState = selectableLazyListState,
            treeState = TreeState(selectableLazyListState),
            tree = processTree,
        )
    }
    override val processOutputUiState: OutputUiState = OutputUiState(
        filters = processOutputFilters,
        isInfoExpanded = processOutputInfoExpanded,
        isOutputExpanded = processOutputOutputExpanded,
        lazyListState = LazyListState(),
    )

    init {
        collectSearchStats()
        collectProcessTree()
        ensureProcessTreeScroll()
    }

    override fun collapseAllContexts() {
        processTreeUiState.treeState.openNodes = setOf()

        ProcessOutputUsageCollector.treeCollapseAllClicked()
    }

    override fun expandAllContexts() {
        processTreeUiState.treeState.openNodes =
            processTreeUiState.tree.value
                .walkDepthFirst()
                .mapNotNull {
                    when (val data = it.data) {
                        is TreeNode.Context -> data.traceContext
                        is TreeNode.Process -> null
                    }
                }
                .toSet()

        ProcessOutputUsageCollector.treeExpandAllClicked()
    }

    override fun selectProcess(process: LoggedProcess?) {
        selectedProcess.value = process
        ProcessOutputUsageCollector.treeProcessSelected()
    }

    override fun toggleTreeFilter(filter: TreeFilter) {
        processTreeFilters.toggle(filter)

        when (filter) {
            TreeFilter.ShowBackgroundProcesses -> {
                ProcessOutputUsageCollector.treeFilterBackgroundProcessesToggled(
                    processTreeFilters.contains(
                        TreeFilter.ShowBackgroundProcesses,
                    ),
                )

                coroutineScope.launch(Dispatchers.EDT) {
                    processTreeUiState.selectableLazyListState.lazyListState.scrollToItem(0)
                }
            }
            TreeFilter.ShowTime ->
                ProcessOutputUsageCollector.treeFilterTimeToggled(
                    processTreeFilters.contains(TreeFilter.ShowTime),
                )
        }
    }

    override fun toggleOutputFilter(filter: OutputFilter) {
        processOutputFilters.toggle(filter)

        when (filter) {
            OutputFilter.ShowTags ->
                ProcessOutputUsageCollector.outputFilterShowTagsToggled(
                    processOutputFilters.contains(OutputFilter.ShowTags),
                )
        }
    }

    override fun toggleProcessInfo() {
        val expanded = processOutputInfoExpanded.value

        processOutputInfoExpanded.value = !expanded

        ProcessOutputUsageCollector.outputProcessInfoToggled(!expanded)
    }

    override fun toggleProcessOutput() {
        val expanded = processOutputOutputExpanded.value

        processOutputOutputExpanded.value = !expanded

        ProcessOutputUsageCollector.outputProcessOutputToggled(!expanded)
    }

    override fun copyOutputToClipboard(loggedProcess: LoggedProcess) {
        val showTags = processOutputUiState.filters.contains(OutputFilter.ShowTags)

        val stringToCopy = buildString {
            loggedProcess.lines.replayCache.forEach { line ->
                if (showTags) {
                    val tag = when (line.kind) {
                        LoggedProcessLine.Kind.ERR -> Tag.ERROR
                        LoggedProcessLine.Kind.OUT -> Tag.OUTPUT
                    }

                    append("[$tag] ".padStart(Tag.maxLength + 3))
                } else {
                    repeat(Tag.maxLength + 3) {
                        append(' ')
                    }
                }

                appendLine(line.text)
            }

            loggedProcess.exitInfo.value?.also {
                append("[${Tag.EXIT}] ".padStart(Tag.maxLength + 3))
                append(it.exitValue)

                it.additionalMessageToUser?.also { message ->
                    append(": ")
                    append(message)
                }

                appendLine()
            }
        }

        CopyPasteManager.copyTextToClipboard(stringToCopy)

        ProcessOutputUsageCollector.outputCopyClicked()
    }

    @RequiresEdt
    override fun tryOpenLogInToolWindow(logId: Int): Boolean {
        val match = loggedProcesses.value.find { process -> process.id == logId }

        if (match == null) {
            return false
        }

        ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID)?.show()

        coroutineScope.launch(Dispatchers.EDT) {
            val process = loggedProcesses.value.find { it.id == logId } ?: return@launch

            // select the process
            selectedProcess.value = process

            // open all the parent nodes of the process
            process.traceContext?.also {
                processTreeUiState.treeState.openNodes(it.hierarchy())
            }

            // select the process in the list state
            processTreeUiState.treeState.selectedKeys = setOf(process)

            // scroll to the top of the list
            processTreeUiState.selectableLazyListState.lazyListState.scrollToItem(0)

            // clear search text
            processTreeUiState.searchState.clearText()

            // expand process output section
            processOutputOutputExpanded.value = true

            // wait until output has recomposed
            delay(100.milliseconds)

            // scroll output all the way to the bottom
            val index = processOutputUiState.lazyListState.layoutInfo.totalItemsCount
            processOutputUiState.lazyListState.scrollToItem(index.coerceAtLeast(0))
        }

        ProcessOutputUsageCollector.toolwindowOpenedDueToError()

        return true
    }

    override fun specifyAdditionalMessageToUser(logId: Int, @Nls message: String) {
        val trimmed = message.trim()

        if (trimmed.isEmpty()) {
            return
        }

        loggedProcesses.value.find { it.id == logId }?.exitInfo?.also { exitInfo ->
            exitInfo.value = exitInfo.value?.copy(additionalMessageToUser = message)
        }
    }

    private fun collectSearchStats() {
        coroutineScope.launch {
            snapshotFlow { processTreeUiState.searchState.text }
                .collect {
                    ProcessOutputUsageCollector.treeSearchEdited()
                }
        }
    }

    private fun collectProcessTree() {
        val backgroundErrorProcesses = MutableStateFlow<Set<Int>>(setOf())

        coroutineScope.launch {
            loggedProcesses
                .collect { list ->
                    backgroundErrorProcesses.value = setOf()
                    list
                        .filter { it.traceContext == NON_INTERACTIVE_ROOT_TRACE_CONTEXT }
                        .forEach { process ->
                            launch {
                                process.exitInfo.collect {
                                    val exitValue = it?.exitValue
                                    if (exitValue != null && exitValue != 0) {
                                        backgroundErrorProcesses.value += process.id
                                    } else {
                                        backgroundErrorProcesses.value -= process.id
                                    }
                                }
                            }
                        }
                }
        }

        combine(
            backgroundErrorProcesses,
            loggedProcesses,
            snapshotFlow { processTreeUiState.searchState.text },
            snapshotFlow { processTreeUiState.filters.toSet() },
        )
        { backgroundErrorProcesses, processList, search, filters ->
            val lowercaseSearch = search.toString().trim().lowercase()
            var filteredProcesses =
                processList
                    .reversed()
                    .filter {
                        it.shortenedCommandString
                            .lowercase()
                            .contains(lowercaseSearch)
                    }

            if (!filters.contains(TreeFilter.ShowBackgroundProcesses)) {
                filteredProcesses = filteredProcesses.filter {
                    it.traceContext != NON_INTERACTIVE_ROOT_TRACE_CONTEXT
                        || backgroundErrorProcesses.contains(it.id)
                }
            }

            data class Node(
                val traceContext: TraceContext? = null,
                val process: LoggedProcess? = null,
                val children: MutableList<Node> = mutableListOf(),
            )

            val root = mutableListOf<Node>()

            filteredProcesses.forEach { process ->
                when (val traceContext = process.traceContext) {
                    null, NON_INTERACTIVE_ROOT_TRACE_CONTEXT -> root += Node(process = process)
                    else -> {
                        val hierarchy = traceContext.hierarchy()
                        var currentRoot = root

                        hierarchy.forEach { currentContext ->
                            val node =
                                currentRoot
                                    .firstOrNull { node ->
                                        node.traceContext == currentContext
                                    }
                                    ?: run {
                                        Node(traceContext = currentContext).also {
                                            currentRoot += it
                                        }
                                    }

                            currentRoot = node.children
                        }

                        currentRoot += Node(process = process)
                    }
                }
            }

            fun TreeGeneratorScope<TreeNode>.buildNodeTree(root: List<Node>) {
                root.forEach { (traceContext, process, children) ->
                    if (traceContext != null) {
                        addNode(TreeNode.Context(traceContext), traceContext) {
                            buildNodeTree(children)
                        }
                    } else if (process != null) {
                        addLeaf(TreeNode.Process(process), process)
                    }
                }
            }

            val newTree = buildTree {
                buildNodeTree(root)
            }

            if (newTree.isEmpty()) {
                selectProcess(null)
            }

            processTree.value = newTree
        }.launchIn(coroutineScope)
    }

    @OptIn(FlowPreview::class)
    private fun ensureProcessTreeScroll() {
        coroutineScope.launch(Dispatchers.EDT) {
            var prevCanScrollBackwards = false
            var prevLastItem: Any? = null

            combine(
                snapshotFlow { processTreeUiState.treeState.canScrollBackward },
                loggedProcesses,
            ) { canScrollBackwards, processes -> canScrollBackwards to processes }
                .debounce(100.milliseconds)
                .collect { (canScrollBackwards, processes) ->
                    val lastItem = processes.lastOrNull()

                    // scroll to the top if an item was added when the list tree fully scrolled to
                    // the top
                    if (canScrollBackwards && !prevCanScrollBackwards && lastItem != prevLastItem) {
                        processTreeUiState.selectableLazyListState.lazyListState.scrollToItem(0)
                    } else {
                        prevCanScrollBackwards = canScrollBackwards
                    }

                    prevLastItem = lastItem
                }
        }
    }
}

internal object Tag {
    const val ERROR = "error"
    const val OUTPUT = "output"
    const val EXIT = "exit"

    val maxLength: Int =
        Tag::class.java.declaredFields
            .filter { it.type == String::class.java }
            .fastMaxOfOrDefault(0) { (it.get(null) as String).length }
}

private fun TraceContext.hierarchy(): List<TraceContext> {
    val hierarchy = mutableListOf<TraceContext>()
    var currentContext: TraceContext? = this

    while (currentContext != null) {
        hierarchy.add(0, currentContext)
        currentContext = currentContext.parentTraceContext
    }

    return hierarchy
}
