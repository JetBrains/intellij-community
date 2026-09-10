package com.intellij.python.processOutput.frontend

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.python.processOutput.common.FrontendTopicListener
import com.intellij.python.processOutput.common.FrontendTopicService
import com.intellij.python.processOutput.common.LoggedProcessDto
import com.intellij.python.processOutput.common.OutputKindDto
import com.intellij.python.processOutput.common.OutputLineDto
import com.intellij.python.processOutput.common.ProcessBinaryFileName
import com.intellij.python.processOutput.common.ProcessIcon
import com.intellij.python.processOutput.common.ProcessId
import com.intellij.python.processOutput.common.ProcessOutputEventDto
import com.intellij.python.processOutput.common.TraceContextDto
import com.intellij.python.processOutput.common.TraceContextKind
import com.intellij.python.processOutput.common.TraceContextUuid
import com.intellij.python.processOutput.frontend.ui.shortenedCommandString
import com.intellij.util.applyIf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.WeakHashMap
import javax.swing.tree.DefaultMutableTreeNode
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

internal object Limits {
  const val MAX_PROCESSES = 512
  const val MAX_LINES = 1024
  val OPEN_TOOL_WINDOW_BY_TRACE_UUID_TIMEOUT = 5.seconds
}

@Service(Service.Level.PROJECT)
internal class ProcessOutputControllerService(coroutineScope: CoroutineScope) {
  val controller: ProcessOutputController
    field = ProcessOutputControllerImpl(
      coroutineScope = coroutineScope,
      frontendTopic = ApplicationManager.getApplication().service<FrontendTopicService>(),
      iconMappingData = ProcessOutputIconMappingDataImpl,
      usageCollector = ProcessOutputUsageCollectorImpl,
      clipboardCopier = { CopyPasteManager.copyTextToClipboard(it) }
    )
}

internal fun interface ClipboardCopier {
  fun copyToClipboard(text: String)
}

internal class ProcessOutputControllerImpl(
  private val coroutineScope: CoroutineScope,
  private val frontendTopic: FrontendTopicListener,
  iconMappingData: ProcessOutputIconMappingData,
  private val usageCollector: ProcessOutputUsageCollector,
  private val clipboardCopier: ClipboardCopier,
) : ProcessOutputController {
  private var processMap = LinkedHashMap<ProcessId, MutableLoggedProcess>()
  private val isInfoExpandedFlow = MutableStateFlow(false)
  private val isOutputExpandedFlow = MutableStateFlow(true)
  private val searchQuery = MutableStateFlow("")
  private val treeRoot = MutableStateFlow<List<ProcessTreeNode>>(emptyList())

  override val uiEvents: Flow<UiEvent>
    field = MutableSharedFlow()
  override val selectedProcess: StateFlow<LoggedProcess?>
    field = MutableStateFlow(null)

  override val treeSectionState: TreeSectionState =
    TreeSectionState(
      filters = FilterActionGroupState(TreeFilter),
      searchQuery = searchQuery,
      treeRoot = treeRoot,
    )

  override val outputSectionState: OutputSectionState = OutputSectionState(
    filters = FilterActionGroupState(OutputFilter),
    isInfoExpanded = isInfoExpandedFlow,
    isOutputExpanded = isOutputExpandedFlow,
  )

  private val traceContextCache = boundedLinkedHashMap<TraceContextUuid, TraceContextDto>(Limits.MAX_PROCESSES * 4)

  private val iconMapping = iconMappingData.mapping
  private val iconMatchers = iconMappingData.matchers
  private val iconCache = WeakHashMap<LoggedProcess, ProcessIcon>()

  init {
    collectTopicEvents()
    collectSearchStats()
    collectTreeState()
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

    usageCollector.log(ProcessOutputUsageEvent.ProcessSelected)
  }

  override fun onTreeFilterItemToggled(filterItem: TreeFilter.Item, enabled: Boolean) {
    usageCollector.log(ProcessOutputUsageEvent.TreeFilterToggled(filterItem, enabled))
  }

  override fun onOutputFilterItemToggled(filterItem: OutputFilter.Item, enabled: Boolean) {
    usageCollector.log(ProcessOutputUsageEvent.OutputFilterToggled(filterItem, enabled))
  }

  override fun toggleProcessInfo() {
    val expanded = isInfoExpandedFlow.value

    isInfoExpandedFlow.value = !expanded

    usageCollector.log(ProcessOutputUsageEvent.ProcessInfoRegionToggled(!expanded))
  }

  override fun toggleProcessOutput() {
    val expanded = isOutputExpandedFlow.value

    isOutputExpandedFlow.value = !expanded

    usageCollector.log(ProcessOutputUsageEvent.ProcessOutputRegionToggled(!expanded))
  }

  override fun copyOutputToClipboard(loggedProcess: LoggedProcess) {
    val showTags = outputSectionState.filters[OutputFilter.Item.SHOW_TAGS]

    val stringToCopy = buildString {
      var lastTag: OutputTag? = null

      loggedProcess.lines.value.forEach { line ->
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

    clipboardCopier.copyToClipboard(stringToCopy)

    usageCollector.log(ProcessOutputUsageEvent.OutputCopyClicked)
  }

  override fun copyOutputTagAtIndexToClipboard(
    loggedProcess: LoggedProcess,
    fromIndex: Int,
  ) {
    val stringToCopy = buildString {
      val lines = loggedProcess.lines.value

      lines
        .drop(fromIndex)
        .takeWhile { it.kind == lines[fromIndex].kind }
        .forEach {
          appendLine(it.text)
        }
    }

    clipboardCopier.copyToClipboard(stringToCopy)

    usageCollector.log(ProcessOutputUsageEvent.TagSectionCopyClicked)
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

    clipboardCopier.copyToClipboard(stringToCopy)

    usageCollector.log(ProcessOutputUsageEvent.ExitInfoCopyClicked)
  }

  private fun collectTopicEvents() {
    val interactiveProcesses = mutableListOf<MutableLoggedProcess>()
    val backgroundProcesses = mutableListOf<MutableLoggedProcess>()

    coroutineScope.launch {
      frontendTopic.events.collect { event ->
        when (event) {
          is ProcessOutputEventDto.NewProcess -> {
            for (traceContext in event.traceHierarchy) {
              if (traceContext.uuid !in traceContextCache) {
                traceContextCache[traceContext.uuid] = traceContext
              }
            }

            val traceContext = traceContextCache[event.loggedProcess.traceContextUuid]
            val loggedProcess = MutableLoggedProcess(
              data = event.loggedProcess,
              lines = MutableStateFlow(emptyList()),
              status = MutableStateFlow(ProcessStatus.Running),
            )

            val listToAppendTo =
              when (traceContext?.kind) {
                TraceContextKind.NON_INTERACTIVE -> backgroundProcesses
                TraceContextKind.INTERACTIVE, null -> interactiveProcesses
              }

            listToAppendTo += loggedProcess

            if (listToAppendTo.size > Limits.MAX_PROCESSES) {
              listToAppendTo.subList(0, listToAppendTo.size - Limits.MAX_PROCESSES).clear()
            }

            val newLoggedProcesses = (interactiveProcesses + backgroundProcesses).sortedBy { it.data.startedAt }

            processMap = newLoggedProcesses.associateByTo(LinkedHashMap()) { it.data.id }

            updateProcessTree()
          }
          is ProcessOutputEventDto.NewOutputLine -> {
            val process = processMap[event.processId]

            if (process != null) {
              val newLines = process.lines.value.toMutableList()

              newLines += event.outputLine

              if (newLines.size > Limits.MAX_LINES) {
                newLines.subList(0, newLines.size - Limits.MAX_LINES).clear()
              }

              process.lines.value = newLines
            }
          }
          is ProcessOutputEventDto.ProcessExit -> {
            val process = processMap[event.processId]

            if (process != null) {
              val kind = traceContextCache[process.data.traceContextUuid]?.kind
              val isBackgroundError =
                when (kind) {
                  TraceContextKind.NON_INTERACTIVE -> event.exitValue != 0
                  TraceContextKind.INTERACTIVE, null -> false
                }

              process.status.value =
                ProcessStatus.Done(
                  exitedAt = event.exitedAt,
                  exitCode = event.exitValue,
                )

              if (isBackgroundError) {
                updateProcessTree()
              }

              uiEvents.emit(UiEvent.StatusUpdate(process))
            }
          }
          is ProcessOutputEventDto.ExecError -> {
            val error = event.execErrorDto
            val process =
              error.loggedProcessId?.let { processId ->
                processMap[processId]
              }

            process?.also {
              when (val status = it.status.value) {
                is ProcessStatus.Done -> {
                  it.status.value = status.copy(additionalMessageToUser = event.execErrorDto.additionalMessageToUser)
                }
                ProcessStatus.Running -> {}
              }
            }

            uiEvents.emit(
              UiEvent.DisplayExecError(
                execErrorDto = error,
                associatedProcess = process,
              )
            )
          }
          is ProcessOutputEventDto.OpenToolWindowByTraceUuid -> {
            coroutineScope.launch {
              val process =
                withTimeoutOrNull(Limits.OPEN_TOOL_WINDOW_BY_TRACE_UUID_TIMEOUT) {
                  lateinit var process: LoggedProcess

                  while (true) {
                    processMap.values.lastOrNull { it.data.traceContextUuid == event.uuid }?.also {
                      process = it
                      break
                    }
                    delay(50.milliseconds)
                  }

                  process
                }

              if (process != null || event.openIfNotFound) {
                uiEvents.emit(
                  UiEvent.DisplayToolWindow(
                    processToSelect = process
                  )
                )
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
        usageCollector.log(ProcessOutputUsageEvent.SearchEdited)
      }
    }
  }

  private fun updateProcessTree() {
    val search = treeSectionState.searchQuery.value
    val filters = treeSectionState.filters.active.value

    updateProcessTree(search, filters)
  }

  private fun updateProcessTree(searchQuery: String, filters: Set<TreeFilter.Item>) {
    val processList = processMap.values

    val lowercaseSearch = searchQuery.trim().lowercase()
    val filteredProcesses =
      processList
        .reversed()
        .filter {
          it.data.shortenedCommandString
            .lowercase()
            .contains(lowercaseSearch)
        }
        .applyIf(!filters.contains(TreeFilter.Item.SHOW_BACKGROUND_PROCESSES)) {
          filter {
            val kind =
              it.data.traceContextUuid
                ?.let { uuid -> traceContextCache[uuid] }
                ?.kind

            when (kind) {
              TraceContextKind.NON_INTERACTIVE ->
                when (val status = it.status.value) {
                  is ProcessStatus.Done -> status.exitCode != 0
                  ProcessStatus.Running -> false
                }
              TraceContextKind.INTERACTIVE, null -> true
            }
          }
        }

    val root = DefaultMutableTreeNode()
    val traceContextMap = mutableMapOf<TraceContextUuid, ProcessTreeNode>()

    for (process in filteredProcesses) {
      val traceContext = process.data.traceContextUuid?.let { traceContextCache[it] }

      when (traceContext?.kind) {
        TraceContextKind.NON_INTERACTIVE, null -> {
          root.add(createProcessNode(process))
        }
        TraceContextKind.INTERACTIVE -> {
          val hierarchy = traceContext.hierarchy()
          var currentRoot = root

          for (currentContext in hierarchy) {
            val existingContext =
              currentRoot
                .childrenOf<ProcessTreeNode.Context>()
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

    treeRoot.value = root.children().toList().map { it as ProcessTreeNode }
  }

  private fun collectTreeState() {
    combine(treeSectionState.searchQuery, treeSectionState.filters.active) { searchQuery, filters ->
      updateProcessTree(searchQuery, filters)
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

private class MutableLoggedProcess(
  override val data: LoggedProcessDto,
  override val lines: MutableStateFlow<List<OutputLineDto>>,
  override val status: MutableStateFlow<ProcessStatus>,
) : LoggedProcess

internal inline fun <reified T : ProcessTreeNode> DefaultMutableTreeNode.childrenOf(): List<T> =
  children().toList().filterIsInstance<T>()

private fun <K, V> boundedLinkedHashMap(maxSize: Int): LinkedHashMap<K, V> =
  object : LinkedHashMap<K, V>(maxSize) {
    override fun removeEldestEntry(eldest: Map.Entry<K, V>): Boolean =
      size > maxSize
  }
