package com.intellij.python.processOutput.frontend

import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.snapshots.SnapshotStateSet
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.python.processOutput.common.FrontendTopicService
import com.intellij.python.processOutput.common.LoggedProcessDto
import com.intellij.python.processOutput.common.OutputKindDto
import com.intellij.python.processOutput.common.OutputLineDto
import com.intellij.python.processOutput.common.ProcessBinaryFileName
import com.intellij.python.processOutput.common.ProcessIcon
import com.intellij.python.processOutput.common.ProcessOutputEventDto
import com.intellij.python.processOutput.common.ProcessOutputQuery
import com.intellij.python.processOutput.common.ProcessWeightDto
import com.intellij.python.processOutput.common.QueryResponsePayload
import com.intellij.python.processOutput.common.TraceContextDto
import com.intellij.python.processOutput.common.TraceContextKind
import com.intellij.python.processOutput.common.TraceContextUuid
import com.intellij.python.processOutput.frontend.ProcessOutputBundle.message
import com.intellij.python.processOutput.frontend.ui.components.OutputSectionTestTags
import com.intellij.python.processOutput.frontend.ui.shortenedCommandString
import java.util.WeakHashMap
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import javax.swing.tree.DefaultMutableTreeNode
import kotlin.time.Duration.Companion.seconds

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
  val processStatusUpdates: Flow<LoggedProcess>
  val processTreeUiState: TreeUiState
  val processOutputUiState: OutputUiState


  fun search(query: String)
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
  val searchQuery: StateFlow<String>,
  val treeRoot: StateFlow<List<ProcessTreeNode>>,
)

@ApiStatus.Internal
class FilterActionGroupState<TFilter, TItem>(treeFilter: TFilter)
  where TItem : Enum<TItem>,
        TItem : FilterItem,
        TFilter : Filter<TItem> {
  internal val active: SnapshotStateSet<TItem> = mutableStateSetOf()

  init {
    active.addAll(treeFilter.defaultActive)
  }
}

@ApiStatus.Internal
interface Filter<TItem>
  where TItem : Enum<TItem>,
        TItem : FilterItem {
  val defaultActive: Set<TItem>
}

@ApiStatus.Internal
interface FilterItem {
  val title: @Nls String
  val testTag: String?
}

@ApiStatus.Internal
object TreeFilter : Filter<TreeFilter.Item> {
  enum class Item(override val title: String, override val testTag: String? = null) : FilterItem {
    SHOW_TIME(message("process.output.filters.tree.time")),
    SHOW_PROCESS_WEIGHT(message("process.output.filters.tree.processWeight")),
    SHOW_BACKGROUND_PROCESSES(message("process.output.filters.tree.backgroundProcesses")),
  }

  override val defaultActive: Set<Item> = setOf(Item.SHOW_TIME, Item.SHOW_PROCESS_WEIGHT)
}

@ApiStatus.Internal
object OutputFilter : Filter<OutputFilter.Item> {
  enum class Item(override val title: String, override val testTag: String) : FilterItem {
    SHOW_TAGS(
      title = message("process.output.filters.output.tags"),
      testTag = OutputSectionTestTags.FILTERS_TAGS,
    ),
    WRAP_CONTENT(
      title = message("process.output.filters.output.wrap"),
      testTag = OutputSectionTestTags.FILTERS_WRAP,
    );
  }

  override val defaultActive: Set<Item> = setOf(Item.SHOW_TAGS, Item.WRAP_CONTENT)
}

@ApiStatus.Internal
sealed class ProcessTreeNode : DefaultMutableTreeNode() {
  abstract val title: @NlsSafe String
  abstract val timestamp: Instant

  val formattedTimestamp: @Nls String
    get() =
      timestamp.formatTime()

  val id: Id
    get() =
      when (this) {
        is Context -> Id.Context(uuid)
        is Process -> Id.Process(loggedProcess.data.id)
      }

  class Context(traceContext: TraceContextDto) : ProcessTreeNode() {
    override val title: @NlsSafe String = traceContext.title
    override val timestamp: Instant = Instant.fromEpochMilliseconds(traceContext.timestamp)
    val uuid: TraceContextUuid = traceContext.uuid
  }

  class Process(
    val loggedProcess: LoggedProcess,
    val isBackground: Boolean,
    val processIcon: ProcessIcon?,
  ) : ProcessTreeNode() {
    override val title: @NlsSafe String = loggedProcess.data.shortenedCommandString
    override val timestamp: Instant = loggedProcess.data.startedAt
    val weight: ProcessWeightDto? = loggedProcess.data.weight

    private val status = loggedProcess.status

    val isRunning: Boolean
      get() =
        when (status.value) {
          is ProcessStatus.Done -> false
          ProcessStatus.Running -> true
        }

    val isCriticalError: Boolean
      get() =
        when (val status = status.value) {
          is ProcessStatus.Done ->
            status.exitCode != 0 && status.isCritical
          ProcessStatus.Running ->
            false
        }

    val isError: Boolean
      get() =
        when (val status = status.value) {
          is ProcessStatus.Done ->
            status.exitCode != 0
          ProcessStatus.Running ->
            false
        }
  }

  sealed interface Id {
    data class Process(val id: Int) : Id
    data class Context(val uuid: TraceContextUuid) : Id
  }
}

@ApiStatus.Internal
data class OutputUiState(
  val filters: FilterActionGroupState<OutputFilter, OutputFilter.Item>,
  val isInfoExpanded: StateFlow<Boolean>,
  val isOutputExpanded: StateFlow<Boolean>,
  val verticalScrollState: ScrollState,
  val horizontalScrollState: ScrollState,
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
  internal val loggedProcesses = MutableStateFlow<List<LoggedProcess>>(listOf())

  private val processOutputInfoExpanded = MutableStateFlow(false)
  private val processOutputOutputExpanded = MutableStateFlow(true)
  private val searchQuery = MutableStateFlow("")
  private val processTreeState = MutableStateFlow<List<ProcessTreeNode>>(emptyList())

  override val processStatusUpdates: Flow<LoggedProcess>
    field = MutableSharedFlow()
  override val selectedProcess: MutableStateFlow<LoggedProcess?> = MutableStateFlow(null)

  override val processTreeUiState: TreeUiState =
    TreeUiState(
      filters = FilterActionGroupState(TreeFilter),
      searchQuery = searchQuery,
      treeRoot = processTreeState,
    )

  override val processOutputUiState: OutputUiState = OutputUiState(
    filters = FilterActionGroupState(OutputFilter),
    isInfoExpanded = processOutputInfoExpanded,
    isOutputExpanded = processOutputOutputExpanded,
    verticalScrollState = ScrollState(0),
    horizontalScrollState = ScrollState(0),
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
  }

  override fun search(query: String) {
    if (searchQuery.value == query) {
      return
    }

    searchQuery.value = query
  }

  override fun selectProcess(process: LoggedProcess?) {
    if (process?.data?.id == selectedProcess.value?.data?.id) {
      return
    }

    selectedProcess.value = process

    coroutineScope.launch(Dispatchers.EDT) {
      processOutputUiState.verticalScrollState.scrollTo(0)
      processOutputUiState.horizontalScrollState.scrollTo(0)
    }

    ProcessOutputUsageCollector.treeProcessSelected()
  }

  override fun onTreeFilterItemToggled(filterItem: TreeFilter.Item, enabled: Boolean) {
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
      var lastTag: OutputTag? = null

      loggedProcess.lines.forEach { line ->
        if (showTags) {
          val tag = when (line.kind) {
            OutputKindDto.OUT -> OutputTag.OUTPUT
            OutputKindDto.ERR -> OutputTag.ERROR
          }

          if (lastTag == tag) {
            append(OutputTag.formatter.blankBracketTagString)
          }
          else {
            append(OutputTag.formatter.bracketedTagString(tag))
          }

          lastTag = tag
        }

        appendLine(line.text)
      }

      val exitData = when (val status = loggedProcess.status.value) {
        ProcessStatus.Running -> null
        is ProcessStatus.Done -> status
      }

      exitData?.also { exitData ->
        if (showTags) {
          append(OutputTag.formatter.bracketedTagString(OutputTag.EXIT))
        }

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
    val toolWindowManager = ToolWindowManager.getInstance(project)

    coroutineScope.launch(Dispatchers.EDT) {
      toolWindowManager.getToolWindow(TOOL_WINDOW_ID)?.show()

      // select the process
      selectedProcess.value = process

      // clear search text
      searchQuery.value = ""

      // expand process output section
      processOutputOutputExpanded.value = true

      // wait until output has recomposed
      delay(100.milliseconds)

      // scroll output all the way to the bottom left
      processOutputUiState.verticalScrollState.scrollTo(
        processOutputUiState.verticalScrollState.maxValue,
      )
      processOutputUiState.horizontalScrollState.scrollTo(0)
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

            loggedProcesses.emit(processList)
          }
          is ProcessOutputEventDto.NewOutputLine -> {
            val internalProcess = processMap[event.processId]

            if (internalProcess != null) {
              Snapshot.withMutableSnapshot {
                val lines = internalProcess.lines

                lines += event.outputLine

                if (lines.size > ProcessOutputControllerServiceLimits.MAX_LINES) {
                  lines.removeRange(
                    0,
                    lines.size - ProcessOutputControllerServiceLimits.MAX_LINES,
                  )
                }
              }
            }
          }
          is ProcessOutputEventDto.ProcessExit -> {
            val internalProcess = processMap[event.processId]

            if (internalProcess != null) {
              internalProcess.status.value =
                ProcessStatus.Done(
                  exitedAt = event.exitedAt,
                  exitCode = event.exitValue,
                )

              processStatusUpdates.emit(
                LoggedProcess(
                  internalProcess.data,
                  internalProcess.lines,
                  internalProcess.status,
                )
              )
            }
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
              is ProcessOutputQuery.OpenToolWindowByTraceUuid -> {
                coroutineScope.launch {
                  try {
                    withTimeout(10.seconds) {
                      val target =
                        loggedProcesses
                          .mapNotNull { list ->
                            list.lastOrNull {
                              it.data.traceContextUuid?.uuid == query.traceUuid
                            }
                          }
                          .first()
                      val hasOpened = tryOpenLogInToolWindow(target.data.id)
                      query.respond(QueryResponsePayload.BooleanPayload(hasOpened))
                    }
                  }
                  catch (_: TimeoutCancellationException) {
                    query.respond(QueryResponsePayload.BooleanPayload(false))
                  }
                }
              }
            }
        }
      }
    }
  }

  private fun collectSearchStats() {
    coroutineScope.launch {
      searchQuery.collect {
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
                  ?.let { uuid -> traceContextCache[uuid] }
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
                this@launch.launch(CoroutineName(CoroutineNames.EXIT_INFO_COLLECTOR)) {
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
      processTreeUiState.searchQuery,
      snapshotFlow { processTreeUiState.filters.active.toSet() },
    )
    { backgroundErrorProcesses, processList, search, filters ->
      val lowercaseSearch = search.trim().lowercase()
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
            ?.let { uuid -> traceContextCache[uuid] }
            ?.kind

          kind != TraceContextKind.NON_INTERACTIVE ||
          backgroundErrorProcesses.contains(it.data.id)
        }
      }

      val root = DefaultMutableTreeNode()
      val traceContextMap = mutableMapOf<TraceContextUuid, ProcessTreeNode>()

      filteredProcesses.forEach { process ->
        val traceContext =
          process.data.traceContextUuid
            ?.let { traceContextCache[it] }

        when {
          traceContext == null || traceContext.kind == TraceContextKind.NON_INTERACTIVE ->
            root.add(createProcessNode(process))
          else -> {
            val hierarchy = traceContext.hierarchy()
            var currentRoot = root

            hierarchy.forEach { currentContext ->
              val existingContext =
                currentRoot
                  .children()
                  .toList()
                  .filterIsInstance<ProcessTreeNode.Context>()
                  .firstOrNull { node -> node.uuid == currentContext.uuid }

              currentRoot =
                if (existingContext != null) {
                  traceContextMap[existingContext.uuid]!!
                }
                else {
                  val newContext = createContextNode(currentContext)

                  currentRoot.add(newContext)
                  traceContextMap[currentContext.uuid] = newContext

                  newContext
                }
            }

            currentRoot.add(createProcessNode(process))
          }
        }
      }

      if (root.childCount == 0) {
        selectProcess(null)
      }

      processTreeState.value = root.children().toList() as List<ProcessTreeNode>
    }.launchIn(coroutineScope)
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

  private fun createProcessNode(loggedProcess: LoggedProcess): ProcessTreeNode {
    val traceContextKind =
      loggedProcess
        .data
        .traceContextUuid
        ?.let { traceContextCache[it] }
        ?.kind
    val isBackground =
      when (traceContextKind) {
        TraceContextKind.NON_INTERACTIVE -> true
        TraceContextKind.INTERACTIVE, null -> false
      }

    return ProcessTreeNode.Process(
      loggedProcess = loggedProcess,
      isBackground = isBackground,
      processIcon = resolveProcessIcon(loggedProcess),
    )
  }

  private fun createContextNode(traceContext: TraceContextDto): ProcessTreeNode =
    ProcessTreeNode.Context(traceContext)

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

private fun <K, V> boundedLinkedHashMap(maxSize: Int): LinkedHashMap<K, V> =
  object : LinkedHashMap<K, V>(maxSize) {
    override fun removeEldestEntry(eldest: Map.Entry<K, V>): Boolean =
      size > maxSize
  }
