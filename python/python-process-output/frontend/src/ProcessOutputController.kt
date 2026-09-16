package com.intellij.python.processOutput.frontend

import com.intellij.openapi.util.NlsSafe
import com.intellij.python.processOutput.common.ExecErrorDto
import com.intellij.python.processOutput.common.LoggedProcessDto
import com.intellij.python.processOutput.common.OutputLineDto
import com.intellij.python.processOutput.common.ProcessIcon
import com.intellij.python.processOutput.common.ProcessId
import com.intellij.python.processOutput.common.ProcessWeightDto
import com.intellij.python.processOutput.common.TraceContextDto
import com.intellij.python.processOutput.common.TraceContextUuid
import com.intellij.python.processOutput.frontend.ProcessOutputBundle.message
import com.intellij.python.processOutput.frontend.ui.shortenedCommandString
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.annotations.Nls
import javax.swing.tree.DefaultMutableTreeNode
import kotlin.time.Instant

internal interface ProcessOutputController {
  val selectedProcess: StateFlow<LoggedProcess?>

  val treeSectionState: TreeSectionState
  val outputSectionState: OutputSectionState
  val uiEvents: Flow<UiEvent>


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

internal sealed interface UiEvent {
  data class StatusUpdate(val loggedProcess: LoggedProcess) : UiEvent
  data class DisplayExecError(val execErrorDto: ExecErrorDto, val associatedProcess: LoggedProcess?) : UiEvent
  data class DisplayToolWindow(val processToSelect: LoggedProcess?) : UiEvent
}

internal interface LoggedProcess {
  val data: LoggedProcessDto
  val lines: StateFlow<List<OutputLineDto>>
  val status: StateFlow<ProcessStatus>
}

internal sealed interface ProcessStatus {
  data object Running : ProcessStatus
  data class Done(
    val exitedAt: Instant,
    val exitCode: Int,
    val additionalMessageToUser: @Nls String? = null,
    val isCritical: Boolean = false,
  ) : ProcessStatus
}

internal data class TreeSectionState(
  val filters: FilterActionGroupState<TreeFilter, TreeFilter.Item>,
  val searchQuery: StateFlow<String>,
  val treeRoot: StateFlow<List<ProcessTreeNode>>,
)

internal class FilterActionGroupState<TFilter, TItem>(treeFilter: TFilter)
  where TItem : Enum<TItem>,
        TItem : FilterItem,
        TFilter : Filter<TItem> {
  internal val active: StateFlow<Set<TItem>>
    field = MutableStateFlow(treeFilter.defaultActive)

  operator fun set(filterItem: TItem, toggled: Boolean) {
    val activeSnapshot = active.value

    active.value =
      if (toggled) {
        activeSnapshot + filterItem
      }
      else {
        activeSnapshot - filterItem
      }
  }

  operator fun get(filterItem: TItem): Boolean =
    filterItem in active.value
}

internal interface Filter<TItem>
  where TItem : Enum<TItem>,
        TItem : FilterItem {
  val defaultActive: Set<TItem>
}

internal interface FilterItem {
  val title: @Nls String
}

internal object TreeFilter : Filter<TreeFilter.Item> {
  enum class Item(override val title: String) : FilterItem {
    SHOW_TIME(message("process.output.filters.tree.time")),
    SHOW_PROCESS_WEIGHT(message("process.output.filters.tree.processWeight")),
    SHOW_BACKGROUND_PROCESSES(message("process.output.filters.tree.backgroundProcesses")),
  }

  override val defaultActive: Set<Item> = setOf(Item.SHOW_TIME, Item.SHOW_PROCESS_WEIGHT)
}

internal object OutputFilter : Filter<OutputFilter.Item> {
  enum class Item(override val title: String) : FilterItem {
    SHOW_TAGS(message("process.output.filters.output.tags")),
    WRAP_CONTENT(message("process.output.filters.output.wrap"));
  }

  override val defaultActive: Set<Item> = setOf(Item.SHOW_TAGS, Item.WRAP_CONTENT)
}

internal sealed class ProcessTreeNode : DefaultMutableTreeNode() {
  abstract val nodeId: Id
  abstract val title: @NlsSafe String
  abstract val timestamp: Instant

  val formattedTimestamp: @Nls String
    get() =
      timestamp.formatTime()

  class Context(traceContext: TraceContextDto) : ProcessTreeNode() {
    override val nodeId: Id.Context = Id.Context(traceContext.uuid)
    override val title: @NlsSafe String = traceContext.title
    override val timestamp: Instant = Instant.fromEpochMilliseconds(traceContext.timestamp)
    val uuid: TraceContextUuid = traceContext.uuid
  }

  class Process(
    val loggedProcess: LoggedProcess,
    val isBackground: Boolean,
    val processIcon: ProcessIcon?,
  ) : ProcessTreeNode() {
    override val nodeId: Id.Process = Id.Process(loggedProcess.data.id)
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
    data class Process(val processId: ProcessId) : Id
    data class Context(val traceContextUuid: TraceContextUuid) : Id
  }
}

internal data class OutputSectionState(
  val filters: FilterActionGroupState<OutputFilter, OutputFilter.Item>,
  val isInfoExpanded: StateFlow<Boolean>,
  val isOutputExpanded: StateFlow<Boolean>,
)
