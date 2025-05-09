package com.intellij.pycharm.community.ide.impl.settings

import com.intellij.diagnostic.DiagnosticBundle
import com.intellij.diagnostic.VMOptions
import com.intellij.openapi.components.service
import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.openapi.wm.impl.status.widget.StatusBarWidgetSettings
import com.intellij.openapi.wm.impl.status.widget.StatusBarWidgetsManager
import com.intellij.ui.dsl.builder.*
import com.jetbrains.python.PyBundle

class MemoryUsageConfigurable : BoundSearchableConfigurable(
  PyBundle.message("settings.memory.group.title"),
  helpTopic = MEMORY_USAGE_SETTINGS_HELP_TOPIC,
  _id = MEMORY_USAGE_SETTINGS_ID) {

  private val MIN_VALUE: Int = 256
  private val MAX_VALUE: Int = 1_000_000

  private fun getSuggestedValue(option: VMOptions.MemoryKind): Int {
    val current = VMOptions.readOption(option, true)
    var suggested = 0

    suggested = VMOptions.readOption(option, false)
    if (suggested <= 0) suggested = current
    if (suggested <= 0) suggested = MIN_VALUE
    return suggested
  }

  override fun createPanel(): DialogPanel {

    val option = VMOptions.MemoryKind.HEAP
    //val current: Int = VMOptions.readOption(option, true)
    val current: Int = 2000
    //val suggested: Int = getSuggestedValue(option)
    val suggested: Int = 5000
    //val file = EditMemorySettingsService.getInstance().userOptionsFile ?: throw IllegalStateException()

    return panel {
      group(PyBundle.message("settings.memory.heap.size.group")) {
        val formatted = if (current == -1) DiagnosticBundle.message("change.memory.unknown") else current.toString()
        //row("Maximum Heap Size (MiB):") {
        row(PyBundle.message("settings.memory.heap.size.label")) {
          intTextField(IntRange(MIN_VALUE, MAX_VALUE))
            //.text(suggested)
            .columns(7)
            .gap(RightGap.SMALL)
            .focused()
            .component

          text(PyBundle.message("settings.memory.heap.size.current.label", formatted))
        }.topGap(TopGap.SMALL)
          .rowComment(PyBundle.message("settings.memory.heap.size.row.comment"))

        row {
          comment(PyBundle.message("settings.memory.heap.size.restarting.warn.label", "AllIcons.General.Warning"))
        }

      }

      val memoryWidgetFactory = StatusBarWidgetFactory.EP_NAME.extensionList.find { it.id == "Memory" }
      val statusBarWidgetSettings = StatusBarWidgetSettings.getInstance()
      if (memoryWidgetFactory != null) {
        val value = statusBarWidgetSettings.isEnabled(memoryWidgetFactory)
        group(PyBundle.message("settings.memory.indicator.group")) {
          row {
            checkBox(PyBundle.message("settings.memory.indicator.checkbox"))
              .selected(value)
              .onChanged {
                val newValue = it.isSelected
                statusBarWidgetSettings.setEnabled(memoryWidgetFactory, newValue)
                ProjectManager.getInstance().openProjects.forEach { project ->
                  project.service<StatusBarWidgetsManager>().updateWidget(memoryWidgetFactory)
                }
              }
          }.rowComment(PyBundle.message("settings.memory.indicator.row.comment"))
        }
      }
    }
  }

  override fun apply() {
    //val previousJupyterSettings = JupyterSettings.getInstance().clone()
    //super.apply()
    //JupyterSettings.settingsChanged(previousJupyterSettings, JupyterSettings.getInstance())
  }

  companion object {
    private const val MEMORY_USAGE_SETTINGS_ID = "PyCharm Memory Usage Settings"
    private const val MEMORY_USAGE_SETTINGS_HELP_TOPIC = "Increasing_Memory_Heap"
  }
}