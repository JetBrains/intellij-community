package com.intellij.python.processOutput.frontend

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector

internal sealed interface ProcessOutputUsageEvent {
  data object ProcessSelected : ProcessOutputUsageEvent
  data object SearchEdited : ProcessOutputUsageEvent
  data class TreeFilterToggled(val filterItem: TreeFilter.Item, val enabled: Boolean) : ProcessOutputUsageEvent
  data object ExpandAllClicked : ProcessOutputUsageEvent
  data object CollapseAllClicked : ProcessOutputUsageEvent
  data class OutputFilterToggled(val filterItem: OutputFilter.Item, val enabled: Boolean) : ProcessOutputUsageEvent
  data object OutputCopyClicked : ProcessOutputUsageEvent
  data object TagSectionCopyClicked : ProcessOutputUsageEvent
  data object ExitInfoCopyClicked : ProcessOutputUsageEvent
  data class ProcessInfoRegionToggled(val enabled: Boolean) : ProcessOutputUsageEvent
  data class ProcessOutputRegionToggled(val enabled: Boolean) : ProcessOutputUsageEvent
  data object ToolWindowOpenedDueToError : ProcessOutputUsageEvent
}

internal fun interface ProcessOutputUsageCollector {
  fun log(event: ProcessOutputUsageEvent)
}

internal object ProcessOutputUsageCollectorImpl : CounterUsagesCollector(), ProcessOutputUsageCollector {
  private val GROUP: EventLogGroup = EventLogGroup("pycharm.processOutputToolWindow", 4)

  private val TOGGLED_FIELD = EventFields.Boolean("enabled")
  private val TOGGLED_TREE_FILTER =
    EventFields.Enum("tree_filter_variant", TreeFilter.Item::class.java)
  private val TOGGLED_OUTPUT_FILTER =
    EventFields.Enum("output_filter_variant", OutputFilter.Item::class.java)
  private val TOGGLED_FILTER_ENABLED = EventFields.Boolean("filter_enabled")

  private val TREE_PROCESS_SELECTED = GROUP.registerEvent("tree.processSelected")
  private val TREE_SEARCH_EDITED = GROUP.registerEvent("tree.searchEdited")
  private val TREE_FILTER_TOGGLED = GROUP.registerVarargEvent(
    "tree.filterToggled",
    TOGGLED_TREE_FILTER,
    TOGGLED_FILTER_ENABLED,
  )
  private val TREE_EXPAND_ALL_CLICKED = GROUP.registerEvent("tree.expandAllClicked")
  private val TREE_COLLAPSE_ALL_CLICKED = GROUP.registerEvent("tree.collapseAllClicked")
  private val OUTPUT_FILTER_TOGGLED = GROUP.registerVarargEvent(
    "output.filterToggled",
    TOGGLED_OUTPUT_FILTER,
    TOGGLED_FILTER_ENABLED,
  )
  private val OUTPUT_COPY_CLICKED = GROUP.registerVarargEvent("output.copyClicked")
  private val OUTPUT_TAG_SECTION_COPY_CLICKED =
    GROUP.registerVarargEvent("output.copyTagSectionClicked")
  private val OUTPUT_EXIT_INFO_COPY_CLICKED =
    GROUP.registerVarargEvent("output.copyExitInfoClicked")
  private val OUTPUT_PROCESS_INFO_TOGGLED = GROUP.registerVarargEvent(
    "output.processInfoToggled",
    TOGGLED_FIELD,
  )
  private val OUTPUT_PROCESS_OUTPUT_TOGGLED = GROUP.registerVarargEvent(
    "output.processOutputToggled",
    TOGGLED_FIELD,
  )
  private val TOOLWINDOW_OPENED_DUE_TO_ERROR = GROUP.registerEvent("toolwindow.openedDueToError")

  override fun getGroup(): EventLogGroup = GROUP

  override fun log(event: ProcessOutputUsageEvent) {
    when (event) {
      ProcessOutputUsageEvent.CollapseAllClicked -> TREE_COLLAPSE_ALL_CLICKED.log()
      ProcessOutputUsageEvent.ExitInfoCopyClicked -> OUTPUT_EXIT_INFO_COPY_CLICKED.log()
      ProcessOutputUsageEvent.ExpandAllClicked -> TREE_EXPAND_ALL_CLICKED.log()
      ProcessOutputUsageEvent.OutputCopyClicked -> OUTPUT_COPY_CLICKED.log()
      is ProcessOutputUsageEvent.OutputFilterToggled ->
        OUTPUT_FILTER_TOGGLED.log(
          TOGGLED_OUTPUT_FILTER.with(event.filterItem),
          TOGGLED_FILTER_ENABLED.with(event.enabled),
        )
      is ProcessOutputUsageEvent.ProcessInfoRegionToggled ->
        OUTPUT_PROCESS_INFO_TOGGLED.log(TOGGLED_FIELD.with(event.enabled))
      is ProcessOutputUsageEvent.ProcessOutputRegionToggled ->
        OUTPUT_PROCESS_OUTPUT_TOGGLED.log(TOGGLED_FIELD.with(event.enabled))
      ProcessOutputUsageEvent.ProcessSelected -> TREE_PROCESS_SELECTED.log()
      ProcessOutputUsageEvent.SearchEdited -> TREE_SEARCH_EDITED.log()
      ProcessOutputUsageEvent.TagSectionCopyClicked -> OUTPUT_TAG_SECTION_COPY_CLICKED.log()
      ProcessOutputUsageEvent.ToolWindowOpenedDueToError -> TOOLWINDOW_OPENED_DUE_TO_ERROR.log()
      is ProcessOutputUsageEvent.TreeFilterToggled ->
        TREE_FILTER_TOGGLED.log(
          TOGGLED_TREE_FILTER.with(event.filterItem),
          TOGGLED_FILTER_ENABLED.with(event.enabled),
        )

    }
  }
}
