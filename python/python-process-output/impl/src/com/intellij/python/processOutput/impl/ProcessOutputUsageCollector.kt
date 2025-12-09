package com.intellij.python.processOutput.impl

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object ProcessOutputUsageCollector : CounterUsagesCollector() {
    private val GROUP: EventLogGroup = EventLogGroup(
        "pycharm.processOutputToolWindow",
        version = 2,
        recorder = "FUS",
        description = "Statistics for PyCharm's Process Output Tool Window",
    )

    private val TOGGLED_FIELD = EventFields.Boolean("enabled")

    private val TREE_PROCESS_SELECTED = GROUP.registerEvent(
        "tree.processSelected",
        "Process selected",
    )
    private val TREE_SEARCH_EDITED = GROUP.registerEvent(
        "tree.searchEdited",
        "Process search field edited",
    )
    private val TREE_FILTER_TIME_TOGGLED = GROUP.registerVarargEvent(
        "tree.filter.timeToggled",
        "Time filter toggled",
        TOGGLED_FIELD,
    )
    private val TREE_FILTER_BACKGROUND_PROCESSES_TOGGLED = GROUP.registerVarargEvent(
        "tree.filter.backgroundProcessesToggled",
        "Background processes filter toggled",
        TOGGLED_FIELD,
    )
    private val TREE_EXPAND_ALL_CLICKED = GROUP.registerEvent(
        "tree.expandAllClicked",
        "Expand all clicked",
    )
    private val TREE_COLLAPSE_ALL_CLICKED = GROUP.registerEvent(
        "tree.collapseAllClicked",
        "Collapse all clicked",
    )
    private val OUTPUT_FILTER_SHOW_TAGS_TOGGLED = GROUP.registerVarargEvent(
        "output.filter.showTagsToggled",
        "Show tags filter toggled",
        TOGGLED_FIELD,
    )
    private val OUTPUT_COPY_CLICKED = GROUP.registerVarargEvent(
        "output.copyClicked",
        "Copy clicked",
    )
    private val OUTPUT_TAG_SECTION_COPY_CLICKED = GROUP.registerVarargEvent(
        "output.copyTagSectionClicked",
        "Copy tag section clicked",
    )
    private val OUTPUT_EXIT_INFO_COPY_CLICKED = GROUP.registerVarargEvent(
        "output.copyExitInfoClicked",
        "Copy exit info clicked",
    )
    private val OUTPUT_PROCESS_INFO_TOGGLED = GROUP.registerVarargEvent(
        "output.processInfoToggled",
        "Process info section toggled",
        TOGGLED_FIELD,
    )
    private val OUTPUT_PROCESS_OUTPUT_TOGGLED = GROUP.registerVarargEvent(
        "output.processOutputToggled",
        "Process output section toggled",
        TOGGLED_FIELD,
    )
    private val TOOLWINDOW_OPENED_DUE_TO_ERROR = GROUP.registerEvent(
        "toolwindow.openedDueToError",
        "Toolwindow opened due to error",
    )

    override fun getGroup(): EventLogGroup = GROUP

    fun treeProcessSelected() {
        TREE_PROCESS_SELECTED.log()
    }

    fun treeSearchEdited() {
        TREE_SEARCH_EDITED.log()
    }

    fun treeFilterTimeToggled(enabled: Boolean) {
        TREE_FILTER_TIME_TOGGLED.log(
            TOGGLED_FIELD.with(enabled),
        )
    }

    fun treeFilterBackgroundProcessesToggled(enabled: Boolean) {
        TREE_FILTER_BACKGROUND_PROCESSES_TOGGLED.log(
            TOGGLED_FIELD.with(enabled),
        )
    }

    fun treeExpandAllClicked() {
        TREE_EXPAND_ALL_CLICKED.log()
    }

    fun treeCollapseAllClicked() {
        TREE_COLLAPSE_ALL_CLICKED.log()
    }

    fun outputFilterShowTagsToggled(enabled: Boolean) {
        OUTPUT_FILTER_SHOW_TAGS_TOGGLED.log(
            TOGGLED_FIELD.with(enabled),
        )
    }

    fun outputCopyClicked() {
        OUTPUT_COPY_CLICKED.log()
    }

    fun outputTagSectionCopyClicked() {
        OUTPUT_TAG_SECTION_COPY_CLICKED.log()
    }

    fun outputExitInfoCopyClicked() {
        OUTPUT_EXIT_INFO_COPY_CLICKED.log()
    }

    fun outputProcessInfoToggled(enabled: Boolean) {
        OUTPUT_PROCESS_INFO_TOGGLED.log(
            TOGGLED_FIELD.with(enabled),
        )
    }

    fun outputProcessOutputToggled(enabled: Boolean) {
        OUTPUT_PROCESS_OUTPUT_TOGGLED.log(
            TOGGLED_FIELD.with(enabled),
        )
    }

    fun toolwindowOpenedDueToError() {
        TOOLWINDOW_OPENED_DUE_TO_ERROR.log()
    }
}
