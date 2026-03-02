package com.intellij.python.processOutput.frontend

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.util.fastMaxOfOrDefault
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.python.processOutput.common.FrontendTopicService
import com.intellij.python.processOutput.common.LoggedProcessDto
import com.intellij.python.processOutput.common.OutputKindDto
import com.intellij.python.processOutput.common.OutputLineDto
import com.intellij.python.processOutput.common.ProcessBinaryFileName
import com.intellij.python.processOutput.common.ProcessIcon
import com.intellij.python.processOutput.common.ProcessOutputEventDto
import com.intellij.python.processOutput.common.ProcessOutputQuery
import com.intellij.python.processOutput.common.QueryResponsePayload
import com.intellij.python.processOutput.common.TraceContextDto
import com.intellij.python.processOutput.common.TraceContextKind
import com.intellij.python.processOutput.common.TraceContextUuid
import com.intellij.python.processOutput.frontend.ProcessOutputBundle.message
import com.intellij.python.processOutput.frontend.ui.components.Filter
import com.intellij.python.processOutput.frontend.ui.components.FilterActionGroupState
import com.intellij.python.processOutput.frontend.ui.components.FilterItem
import com.intellij.python.processOutput.frontend.ui.components.OutputSectionTestTags
import com.intellij.python.processOutput.frontend.ui.components.TreeSectionTestTags
import com.intellij.python.processOutput.frontend.ui.shortenedCommandString
import java.util.WeakHashMap
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.jewel.foundation.lazy.SelectableLazyListState
import org.jetbrains.jewel.foundation.lazy.tree.Tree
import org.jetbrains.jewel.foundation.lazy.tree.TreeGeneratorScope
import org.jetbrains.jewel.foundation.lazy.tree.TreeState
import org.jetbrains.jewel.foundation.lazy.tree.buildTree

@ApiStatus.Internal
object CoroutineNames {
    const val EXIT_INFO_COLLECTOR: String = "ProcessOutput.ExitInfoCollector"
}

internal object ProcessOutputControllerServiceLimits {
    const val MAX_PROCESSES = 512
    const val MAX_LINES = 1024
}

@ApiStatus.Internal
sealed interface ProcessStatus {
    data object Running : ProcessStatus
    data class Done(
        val exitedAt: Instant,
        val exitCode: Int,
        val additionalMessageToUser: @Nls String? = null,
        val isCritical: Boolean = false,
    ) : ProcessStatus
}

@ApiStatus.Internal
data class LoggedProcess(
    val data: LoggedProcessDto,
    val lines: List<OutputLineDto>,
    val status: StateFlow<ProcessStatus>,
)

internal interface ProcessOutputController {
    val selectedProcess: StateFlow<LoggedProcess?>
    val processTreeUiState: TreeUiState
    val processOutputUiState: OutputUiState

    fun resolveTraceContext(uuid: TraceContextUuid): TraceContextDto?

    fun collapseAllContexts()
    fun expandAllContexts()
    fun selectProcess(process: LoggedProcess?)

    fun onTreeFilterItemToggled(filterItem: TreeFilter.Item, enabled: Boolean)
    fun onOutputFilterItemToggled(filterItem: OutputFilter.Item, enabled: Boolean)
    fun toggleProcessInfo()
    fun toggleProcessOutput()

    fun copyOutputToClipboard(loggedProcess: LoggedProcess)
    fun copyOutputTagAtIndexToClipboard(loggedProcess: LoggedProcess, fromIndex: Int)
    fun copyOutputExitInfoToClipboard(loggedProcess: LoggedProcess)
}

@ApiStatus.Internal
data class TreeUiState(
    val filters: FilterActionGroupState<TreeFilter, TreeFilter.Item>,
    val searchState: TextFieldState,
    val selectableLazyListState: SelectableLazyListState,
    val treeState: TreeState,
    val tree: StateFlow<Tree<TreeNode>>,
)

@ApiStatus.Internal
object TreeFilter : Filter<TreeFilter.Item> {
    enum class Item(override val title: String, override val testTag: String) : FilterItem {
        SHOW_TIME(
            title = message("process.output.filters.tree.time"),
            testTag = TreeSectionTestTags.FILTERS_TIME,
        ),
        SHOW_PROCESS_WEIGHT(
            title = message("process.output.filters.tree.processWeight"),
            testTag = TreeSectionTestTags.FILTERS_PROCESS_WEIGHTS,
        ),
        SHOW_BACKGROUND_PROCESSES(
            title = message("process.output.filters.tree.backgroundProcesses"),
            testTag = TreeSectionTestTags.FILTERS_BACKGROUND,
        ),
    }

    override val defaultActive: Set<Item> = setOf(Item.SHOW_TIME, Item.SHOW_PROCESS_WEIGHT)
}

@ApiStatus.Internal
sealed interface TreeNode {
    data class Context(
        val traceContext: TraceContextDto,
    ) : TreeNode

    data class Process(
        val process: LoggedProcess,
        val icon: ProcessIcon?,
    ) : TreeNode
}

@ApiStatus.Internal
object OutputFilter : Filter<OutputFilter.Item> {
    enum class Item(override val title: String, override val testTag: String) : FilterItem {
        SHOW_TAGS(
            title = message("process.output.filters.output.tags"),
            testTag = OutputSectionTestTags.FILTERS_TAGS,
        );
    }

    override val defaultActive: Set<Item> = setOf(Item.SHOW_TAGS)
}

@ApiStatus.Internal
data class OutputUiState(
    val filters: FilterActionGroupState<OutputFilter, OutputFilter.Item>,
    val isInfoExpanded: StateFlow<Boolean>,
    val isOutputExpanded: StateFlow<Boolean>,
    val lazyListState: LazyListState,
)

internal class InternalLoggedProcess(
    val data: LoggedProcessDto,
    val lines: SnapshotStateList<OutputLineDto>,
    val status: MutableStateFlow<ProcessStatus>,
)

@Service(Service.Level.PROJECT)
internal class ProcessOutputControllerService(
    private val project: Project,
    private val coroutineScope: CoroutineScope,
) : ProcessOutputController {
    private val shouldScrollToTop = MutableStateFlow(false)
    internal val loggedProcesses = MutableStateFlow<List<LoggedProcess>>(listOf())

    private val processTree = MutableStateFlow(value = buildTree<TreeNode> {})

    private val processOutputInfoExpanded = MutableStateFlow(false)
    private val processOutputOutputExpanded = MutableStateFlow(true)

    override val selectedProcess: MutableStateFlow<LoggedProcess?> = MutableStateFlow(null)
    override val processTreeUiState: TreeUiState = run {
        val selectableLazyListState = SelectableLazyListState(LazyListState())
        TreeUiState(
            filters = FilterActionGroupState(TreeFilter),
            searchState = TextFieldState(),
            selectableLazyListState = selectableLazyListState,
            treeState = TreeState(selectableLazyListState),
            tree = processTree,
        )
    }
    override val processOutputUiState: OutputUiState = OutputUiState(
        filters = FilterActionGroupState(OutputFilter),
        isInfoExpanded = processOutputInfoExpanded,
        isOutputExpanded = processOutputOutputExpanded,
        lazyListState = LazyListState(),
    )

    private val traceContextCache = boundedLinkedHashMap<TraceContextUuid, TraceContextDto>(
        ProcessOutputControllerServiceLimits.MAX_PROCESSES * 2,
    )

    private val iconMapping = ProcessOutputIconMappingData.mapping
    private val iconMatchers = ProcessOutputIconMappingData.matchers
    private val iconCache = WeakHashMap<LoggedProcess, ProcessIcon>()

    init {
        collectTopicEvents()
        collectSearchStats()
        collectProcessTree()
        ensureProcessTreeScroll()
    }

    override fun resolveTraceContext(uuid: TraceContextUuid): TraceContextDto? =
        traceContextCache[uuid]

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

    override fun onTreeFilterItemToggled(filterItem: TreeFilter.Item, enabled: Boolean) {
        when (filterItem) {
            TreeFilter.Item.SHOW_BACKGROUND_PROCESSES ->
                coroutineScope.launch(Dispatchers.EDT) {
                    processTreeUiState.selectableLazyListState.lazyListState.scrollToItem(0)
                }
            TreeFilter.Item.SHOW_TIME, TreeFilter.Item.SHOW_PROCESS_WEIGHT -> {}
        }

        ProcessOutputUsageCollector.treeFilterToggled(filterItem, enabled)
    }

    override fun onOutputFilterItemToggled(filterItem: OutputFilter.Item, enabled: Boolean) {
        ProcessOutputUsageCollector.outputFilterToggled(filterItem, enabled)
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
        val showTags = processOutputUiState.filters.active.contains(OutputFilter.Item.SHOW_TAGS)

        val stringToCopy = buildString {
            loggedProcess.lines.forEach { line ->
                if (showTags) {
                    val tag = when (line.kind) {
                        OutputKindDto.OUT -> Tag.OUTPUT
                        OutputKindDto.ERR -> Tag.ERROR
                    }

                    append("[$tag] ".padStart(Tag.maxLength + 3))
                } else {
                    repeat(Tag.maxLength + 3) {
                        append(' ')
                    }
                }

                appendLine(line.text)
            }

            val exitData = when (val status = loggedProcess.status.value) {
                ProcessStatus.Running -> null
                is ProcessStatus.Done -> status
            }

            exitData?.also { exitData ->
                append("[${Tag.EXIT}] ".padStart(Tag.maxLength + 3))
                append(exitData.exitCode)

                exitData.additionalMessageToUser?.also { message ->
                    append(": ")
                    append(message)
                }

                appendLine()
            }
        }

        CopyPasteManager.copyTextToClipboard(stringToCopy)

        ProcessOutputUsageCollector.outputCopyClicked()
    }

    override fun copyOutputTagAtIndexToClipboard(
        loggedProcess: LoggedProcess,
        fromIndex: Int,
    ) {
        val stringToCopy = buildString {
            val lines = loggedProcess.lines

            lines
                .drop(fromIndex)
                .takeWhile { it.kind == lines[fromIndex].kind }
                .forEach {
                    appendLine(it.text)
                }
        }

        CopyPasteManager.copyTextToClipboard(stringToCopy)

        ProcessOutputUsageCollector.outputTagSectionCopyClicked()
    }

    override fun copyOutputExitInfoToClipboard(loggedProcess: LoggedProcess) {
        val exitData = when (val status = loggedProcess.status.value) {
            ProcessStatus.Running -> return
            is ProcessStatus.Done -> status
        }
        val stringToCopy = buildString {
            append(exitData.exitCode)

            exitData.additionalMessageToUser?.also { message ->
                append(": ")
                append(message)
            }

            appendLine()
        }

        CopyPasteManager.copyTextToClipboard(stringToCopy)

        ProcessOutputUsageCollector.outputExitInfoCopyClicked()
    }

    private fun tryOpenLogInToolWindow(logId: Int): Boolean {
        val process = loggedProcesses.value.find { process -> process.data.id == logId }
            ?: return false

        coroutineScope.launch(Dispatchers.EDT) {
            ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID)?.show()

            // select the process
            selectedProcess.value = process

            // open all the parent nodes of the process
            process.data.traceContextUuid
                ?.let { resolveTraceContext(it)  }
                ?.also {
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

    private fun collectTopicEvents() {
        val processMap = boundedLinkedHashMap<Int, InternalLoggedProcess>(
            ProcessOutputControllerServiceLimits.MAX_PROCESSES,
        )
        var processList = listOf<LoggedProcess>()

        coroutineScope.launch {
            val service = ApplicationManager.getApplication().service<FrontendTopicService>()
            service.events.collect { event ->
                when (event) {
                    is ProcessOutputEventDto.NewProcess -> {
                        // If a new item was added while the process tree is fully scrolled to top,
                        // we need to manually scroll all the way to top once a new item is added to
                        // the list state, as it is not done automatically.
                        if (!processTreeUiState.treeState.canScrollBackward) {
                            shouldScrollToTop.value = true
                        }

                        for (traceContext in event.traceHierarchy) {
                            if (traceContext.uuid !in traceContextCache) {
                                traceContextCache[traceContext.uuid] = traceContext
                            }
                        }

                        val internalProcess = InternalLoggedProcess(
                            data = event.loggedProcess,
                            lines = SnapshotStateList(),
                            status = MutableStateFlow(ProcessStatus.Running),
                        )

                        processMap[event.loggedProcess.id] = internalProcess
                        processList = processList + LoggedProcess(
                            data = internalProcess.data,
                            lines = internalProcess.lines,
                            status = internalProcess.status,
                        )

                        if (processList.size > ProcessOutputControllerServiceLimits.MAX_PROCESSES) {
                            processList = processList.drop(
                                processList.size -
                                    ProcessOutputControllerServiceLimits.MAX_PROCESSES,
                            )
                        }

                        event.loggedProcess.traceContextUuid
                            ?.let { resolveTraceContext(it) }
                            ?.takeIf { context ->
                                context.kind != TraceContextKind.NON_INTERACTIVE
                            }
                            ?.also { context ->
                                processTreeUiState.treeState.openNodes(context.hierarchy())
                            }

                        loggedProcesses.emit(processList)
                    }
                    is ProcessOutputEventDto.NewOutputLine -> {
                        val internalProcess = processMap[event.processId]

                        if (internalProcess != null) {
                            Snapshot.withMutableSnapshot {
                                val lines = internalProcess.lines

                                lines += event.outputLine
                                lines.sortBy { it.lineNo }

                                if (lines.size > ProcessOutputControllerServiceLimits.MAX_LINES) {
                                    lines.drop(
                                        lines.size - ProcessOutputControllerServiceLimits.MAX_LINES,
                                    )
                                }
                            }
                        }
                    }
                    is ProcessOutputEventDto.ProcessExit -> {
                        val internalProcess = processMap[event.processId]
                        internalProcess?.status?.emit(
                            ProcessStatus.Done(
                                exitedAt = event.exitedAt,
                                exitCode = event.exitValue,
                            ),
                        )
                    }
                    is ProcessOutputEventDto.ReceivedQuery<*> ->
                        when (val query = event.query) {
                            is ProcessOutputQuery.OpenToolWindowWithError -> {
                                val hasOpened = tryOpenLogInToolWindow(query.processId)
                                query.respond(QueryResponsePayload.BooleanPayload(hasOpened))
                            }
                            is ProcessOutputQuery.SpecifyAdditionalMessageToUser -> {
                                processMap[query.processId]?.also { internalProcess ->
                                    when (val status = internalProcess.status.value) {
                                        ProcessStatus.Running -> {}
                                        is ProcessStatus.Done -> {
                                            internalProcess.status.emit(
                                                status.copy(
                                                    additionalMessageToUser = query.messageToUser,
                                                    isCritical = true,
                                                ),
                                            )
                                        }
                                    }
                                }

                                query.respond(QueryResponsePayload.UnitPayload)
                            }
                        }
                }
            }
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

    @OptIn(FlowPreview::class)
    private fun collectProcessTree() {
        val backgroundErrorProcesses = MutableStateFlow<Set<Int>>(setOf())
        val backgroundObservingCoroutines = mutableListOf<Job>()

        coroutineScope.launch {
            loggedProcesses
                .debounce(100.milliseconds)
                .collect { list ->
                    for (coroutine in backgroundObservingCoroutines) {
                        coroutine.cancelAndJoin()
                    }
                    backgroundObservingCoroutines.clear()

                    backgroundErrorProcesses.value = setOf()

                    list
                        .filter {
                            val kind =
                                it.data.traceContextUuid
                                    ?.let { uuid -> resolveTraceContext(uuid) }
                                    ?.kind

                            when (kind) {
                                TraceContextKind.NON_INTERACTIVE -> true
                                TraceContextKind.INTERACTIVE, null -> false
                            }
                        }
                        .forEach { process ->
                            val exitData = when (val status = process.status.value) {
                                ProcessStatus.Running -> null
                                is ProcessStatus.Done -> status
                            }

                            if (exitData != null) {
                                if (exitData.exitCode != 0) {
                                    backgroundErrorProcesses.value += process.data.id
                                }
                                return@forEach
                            }

                            backgroundObservingCoroutines +=
                                launch(CoroutineName(CoroutineNames.EXIT_INFO_COLLECTOR)) {
                                    process.status.collect {
                                        when (it) {
                                            is ProcessStatus.Done if it.exitCode != 0 ->
                                                backgroundErrorProcesses.value += process.data.id
                                            else ->
                                                backgroundErrorProcesses.value -= process.data.id
                                        }
                                    }
                                }

                        }
                }
        }

        combine(
            backgroundErrorProcesses,
            loggedProcesses.debounce(100.milliseconds),
            snapshotFlow { processTreeUiState.searchState.text },
            snapshotFlow { processTreeUiState.filters.active.toSet() },
        )
        { backgroundErrorProcesses, processList, search, filters ->
            val lowercaseSearch = search.toString().trim().lowercase()
            var filteredProcesses =
                processList
                    .reversed()
                    .filter {
                        it.data.shortenedCommandString
                            .lowercase()
                            .contains(lowercaseSearch)
                    }

            if (!filters.contains(TreeFilter.Item.SHOW_BACKGROUND_PROCESSES)) {
                filteredProcesses = filteredProcesses.filter {
                    val kind = it.data.traceContextUuid
                        ?.let { uuid -> resolveTraceContext(uuid) }
                        ?.kind

                    kind != TraceContextKind.NON_INTERACTIVE ||
                        backgroundErrorProcesses.contains(it.data.id)
                }
            }

            data class Node(
                val traceContext: TraceContextDto? = null,
                val process: LoggedProcess? = null,
                val children: MutableList<Node> = mutableListOf(),
            )

            val root = mutableListOf<Node>()

            filteredProcesses.forEach { process ->
                val traceContext =
                    process.data.traceContextUuid
                        ?.let { resolveTraceContext(it) }
                when {
                    traceContext == null || traceContext.kind == TraceContextKind.NON_INTERACTIVE ->
                        root += Node(process = process)
                    else -> {
                        val hierarchy = traceContext.hierarchy()
                        var currentRoot = root

                        hierarchy.forEach { currentContext ->
                            val node =
                                currentRoot
                                    .firstOrNull { node ->
                                        node.traceContext
                                            ?.let { it.uuid == currentContext.uuid } == true
                                    }
                                    ?: Node(traceContext = currentContext).also {
                                        currentRoot += it
                                    }

                            currentRoot = node.children
                        }

                        currentRoot += Node(process = process)
                    }
                }
            }

            fun TreeGeneratorScope<TreeNode>.buildNodeTree(
                root: List<Node>,
            ) {
                root.forEach { (traceContext, process, children) ->
                    if (traceContext != null) {
                        addNode(
                            TreeNode.Context(traceContext),
                            traceContext,
                        ) {
                            buildNodeTree(children)
                        }
                    } else if (process != null) {
                        addLeaf(
                            TreeNode.Process(
                                process,
                                resolveProcessIcon(process),
                            ),
                            process,
                        )
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

    private fun ensureProcessTreeScroll() {
        coroutineScope.launch(Dispatchers.EDT) {
            combine(
                snapshotFlow { processTreeUiState.selectableLazyListState.firstVisibleItemIndex },
                shouldScrollToTop,
            ) { firstVisibleIndex, processes -> (firstVisibleIndex > 0) to processes }
                .collect { (canScrollBackwards, shouldScrollToTopValue) ->
                    if (canScrollBackwards && shouldScrollToTopValue) {
                        shouldScrollToTop.value = false
                        processTreeUiState.selectableLazyListState.scrollToItem(0)
                    }
                }
        }
    }

    private fun resolveProcessIcon(loggedProcess: LoggedProcess): ProcessIcon? {
        iconCache[loggedProcess]?.also {
            return it
        }

        val exe = loggedProcess.data.exe.parts.lastOrNull() ?: return null
        val exeWithoutExt = exe.substringBeforeLast('.')

        iconMapping[ProcessBinaryFileName(exeWithoutExt)]?.also {
            iconCache[loggedProcess] = it
            return it
        }

        for (matcher in iconMatchers) {
            if (matcher.matcher(ProcessBinaryFileName(exeWithoutExt))) {
                iconCache[loggedProcess] = matcher.icon
                return matcher.icon
            }
        }

        return null
    }

    private fun TraceContextDto.hierarchy(): List<TraceContextDto> {
        val hierarchy = mutableListOf<TraceContextDto>()
        var currentContext: TraceContextDto? = this

        while (currentContext != null) {
            hierarchy.add(0, currentContext)
            currentContext = currentContext.parentUuid?.let { traceContextCache[it] }
        }

        return hierarchy
    }
}

internal object Tag {
    val ERROR = message("process.output.output.tag.stdout")
    val OUTPUT = message("process.output.output.tag.stderr")
    val EXIT = message("process.output.output.tag.exit")

    val maxLength: Int =
        Tag::class.java.declaredFields
            .filter { it.type == String::class.java }
            .fastMaxOfOrDefault(0) { (it.get(null) as String).length }
}

private fun <K, V> boundedLinkedHashMap(maxSize: Int): LinkedHashMap<K, V> =
    object : LinkedHashMap<K, V>(maxSize) {
        override fun removeEldestEntry(eldest: Map.Entry<K, V>): Boolean =
            size > maxSize
    }

