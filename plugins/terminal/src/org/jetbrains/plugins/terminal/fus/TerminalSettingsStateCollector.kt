// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.fus

import com.intellij.ide.util.PropertiesComponent
import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventId
import com.intellij.internal.statistic.eventLog.events.EventId1
import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector
import com.intellij.openapi.util.SystemInfo
import com.intellij.terminal.TerminalUiSettingsManager
import org.jetbrains.plugins.terminal.TerminalCommandHandlerCustomizer.Constants
import org.jetbrains.plugins.terminal.TerminalOptionsProvider
import org.jetbrains.plugins.terminal.block.BlockTerminalOptions
import org.jetbrains.plugins.terminal.block.prompt.TerminalPromptStyle
import org.jetbrains.plugins.terminal.settings.TerminalLocalOptions
import org.jetbrains.plugins.terminal.settings.TerminalOsSpecificOptions

internal class TerminalSettingsStateCollector : ApplicationUsagesCollector() {
  private val GROUP = EventLogGroup("terminalShell.settings", 2)

  private val NON_DEFAULT_OPTIONS = GROUP.registerEvent(
    "non.default.options",
    EventFields.Enum<BooleanOptions>("option_name") { it.settingName },
    EventFields.Enabled
  )
  private val NON_DEFAULT_SHELL = GROUP.registerEvent("non.default.shell", "User modified the default shell path")
  private val NON_DEFAULT_TAB_NAME = GROUP.registerEvent("non.default.tab.name", "User modified the default terminal tab name")
  private val NON_DEFAULT_CURSOR_SHAPE = GROUP.registerEvent(
    "non.default.cursor.shape",
    EventFields.Enum<TerminalUiSettingsManager.CursorShape>("shape")
  )
  private val NON_DEFAULT_PROMPT_STYLE = GROUP.registerEvent(
    "non.default.prompt.style",
    EventFields.Enum<TerminalPromptStyle>("style")
  )

  override fun getGroup(): EventLogGroup = GROUP

  override fun getMetrics(): Set<MetricEvent> {
    val metrics = mutableSetOf<MetricEvent>()
    addNonDefaultBooleanOptions(metrics)

    addIfNotDefault(metrics, NON_DEFAULT_SHELL, TerminalLocalOptions.getInstance().shellPath, null)
    addIfNotDefault(metrics, NON_DEFAULT_TAB_NAME, TerminalOptionsProvider.instance.tabName, TerminalOptionsProvider.State().myTabName)

    addIfNotDefault(
      metrics,
      NON_DEFAULT_CURSOR_SHAPE,
      curValue = TerminalOptionsProvider.instance.cursorShape,
      defaultValue = TerminalOptionsProvider.State().cursorShape
    )

    addIfNotDefault(
      metrics,
      NON_DEFAULT_PROMPT_STYLE,
      curValue = BlockTerminalOptions.getInstance().promptStyle,
      defaultValue = BlockTerminalOptions.State().promptStyle
    )

    return metrics
  }

  private fun addNonDefaultBooleanOptions(metrics: MutableSet<MetricEvent>) {
    val curOptions = TerminalOptionsProvider.instance.state
    val defaultOptions = TerminalOptionsProvider.State()

    addBooleanIfNotDefault(metrics, BooleanOptions.ENABLE_AUDIBLE_BELL, curOptions, defaultOptions) { it.mySoundBell }
    addBooleanIfNotDefault(metrics, BooleanOptions.CLOSE_ON_SESSION_END, curOptions, defaultOptions) { it.myCloseSessionOnLogout }
    addBooleanIfNotDefault(metrics, BooleanOptions.REPORT_MOUSE, curOptions, defaultOptions) { it.myReportMouse }
    addBooleanIfNotDefault(metrics, BooleanOptions.PASTE_ON_MIDDLE_MOUSE_BUTTON, curOptions, defaultOptions) { it.myPasteOnMiddleMouseButton }
    addBooleanIfNotDefault(metrics, BooleanOptions.OVERRIDE_IDE_SHORTCUTS, curOptions, defaultOptions) { it.myOverrideIdeShortcuts }
    addBooleanIfNotDefault(metrics, BooleanOptions.ENABLE_SHELL_INTEGRATION, curOptions, defaultOptions) { it.myShellIntegration }
    addBooleanIfNotDefault(metrics, BooleanOptions.HIGHLIGHT_HYPERLINKS, curOptions, defaultOptions) { it.myHighlightHyperlinks }
    addBooleanIfNotDefault(metrics, BooleanOptions.USE_OPTION_AS_META, curOptions, defaultOptions) { it.useOptionAsMetaKey }

    addIfNotDefault(
      metrics,
      BooleanOptions.COPY_ON_SELECTION,
      curValue = TerminalOsSpecificOptions.getInstance().copyOnSelection,
      defaultValue = SystemInfo.isLinux
    )

    val curBlockOptions = BlockTerminalOptions.getInstance().state
    val defaultBlockOptions = BlockTerminalOptions.State()
    addBooleanIfNotDefault(metrics, BooleanOptions.SHOW_SEPARATORS_BETWEEN_COMMANDS, curBlockOptions, defaultBlockOptions) { it.showSeparatorsBetweenBlocks }

    addIfNotDefault(
      metrics,
      BooleanOptions.RUN_COMMANDS_USING_IDE,
      curValue = PropertiesComponent.getInstance().getBoolean(Constants.TERMINAL_CUSTOM_COMMAND_EXECUTION, Constants.TERMINAL_CUSTOM_COMMAND_EXECUTION_DEFAULT),
      defaultValue = Constants.TERMINAL_CUSTOM_COMMAND_EXECUTION_DEFAULT
    )
  }

  private inline fun <T> addBooleanIfNotDefault(
    metrics: MutableSet<MetricEvent>,
    option: BooleanOptions,
    curState: T,
    defaultState: T,
    valueFunction: (T) -> Boolean,
  ) {
    val curValue = valueFunction(curState)
    val defaultValue = valueFunction(defaultState)
    addIfNotDefault(metrics, option, curValue, defaultValue)
  }

  private fun addIfNotDefault(metrics: MutableSet<MetricEvent>, option: BooleanOptions, curValue: Boolean, defaultValue: Boolean) {
    if (curValue != defaultValue) {
      metrics.add(NON_DEFAULT_OPTIONS.metric(option, curValue))
    }
  }

  private fun <T> addIfNotDefault(metrics: MutableSet<MetricEvent>, event: EventId, curValue: T, defaultValue: T) {
    if (curValue != defaultValue) {
      metrics.add(event.metric())
    }
  }

  private fun <T> addIfNotDefault(metrics: MutableSet<MetricEvent>, event: EventId1<T>, curValue: T, defaultValue: T) {
    if (curValue != defaultValue) {
      metrics.add(event.metric(curValue))
    }
  }

  private enum class BooleanOptions(val settingName: String) {
    ENABLE_AUDIBLE_BELL("enable_audible_bell"),
    CLOSE_ON_SESSION_END("close_on_session_end"),
    REPORT_MOUSE("report_mouse"),
    COPY_ON_SELECTION("copy_on_selection"),
    PASTE_ON_MIDDLE_MOUSE_BUTTON("paste_on_middle_mouse_button"),
    OVERRIDE_IDE_SHORTCUTS("override_ide_shortcuts"),
    ENABLE_SHELL_INTEGRATION("enable_shell_integration"),
    HIGHLIGHT_HYPERLINKS("highlight_hyperlinks"),
    USE_OPTION_AS_META("use_option_as_meta"),
    RUN_COMMANDS_USING_IDE("run_commands_using_ide"),
    SHOW_SEPARATORS_BETWEEN_COMMANDS("show_separators_between_commands"),
  }
}
