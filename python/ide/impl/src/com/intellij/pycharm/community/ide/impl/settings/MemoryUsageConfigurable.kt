package com.intellij.pycharm.community.ide.impl.settings

import com.intellij.diagnostic.DiagnosticBundle
import com.intellij.diagnostic.EditMemorySettingsService
import com.intellij.diagnostic.VMOptions
import com.intellij.openapi.components.service
import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.options.OptionsBundle
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.openapi.wm.impl.status.MemoryUsagePanel
import com.intellij.openapi.wm.impl.status.widget.StatusBarWidgetSettings
import com.intellij.openapi.wm.impl.status.widget.StatusBarWidgetsManager
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.*
import com.intellij.util.ui.RestartDialogImpl
import com.jetbrains.python.PyBundle
import java.io.IOException

class MemoryUsageConfigurable : BoundSearchableConfigurable(
  PyBundle.message("settings.memory.group.title"),
  helpTopic = MEMORY_USAGE_SETTINGS_HELP_TOPIC,
  _id = MEMORY_USAGE_SETTINGS_ID) {

  private val MIN_VALUE: Int = 256
  private val MAX_VALUE: Int = 1_000_000
  private val HEAP_INCREMENT: Int = 512
  private val option = VMOptions.MemoryKind.HEAP

  private val initialHeapSize: Int = getInitialHeapSize()
  private var newHeapSize: String = initialHeapSize.toString()
  private lateinit var newHeapSizeField: JBTextField

  override fun createPanel(): DialogPanel {
    val formattedInitialHeapSize = if (initialHeapSize == -1) DiagnosticBundle.message("change.memory.unknown") else initialHeapSize
    val suggestedHeapSize = getSuggestedValue()

    return panel {
      group(PyBundle.message("settings.memory.heap.size.group")) {
        row(PyBundle.message("settings.memory.heap.size.label")) {
          newHeapSizeField = intTextField(IntRange(MIN_VALUE, MAX_VALUE))
            .text(suggestedHeapSize.toString())
            .columns(7)
            .gap(RightGap.SMALL)
            .focused()
            .onChanged {
              newHeapSize = it.text
            }
            .component

          text(PyBundle.message("settings.memory.heap.size.current.label", formattedInitialHeapSize))

        }
          .topGap(TopGap.SMALL)
          .rowComment(PyBundle.message("settings.memory.heap.size.row.comment"))

        row {
          comment(PyBundle.message("settings.memory.heap.size.restarting.warn.label", "AllIcons.General.Warning"))
        }
      }

      val statusBarWidgetSettings = StatusBarWidgetSettings.getInstance()
      val memoryWidgetFactory = StatusBarWidgetFactory.EP_NAME.extensionList.find { it.id == MemoryUsagePanel.WIDGET_ID }
      if (memoryWidgetFactory != null) {
        val initialCheckboxValue = statusBarWidgetSettings.isEnabled(memoryWidgetFactory)

        group(PyBundle.message("settings.memory.indicator.group")) {
          row {
            checkBox(PyBundle.message("settings.memory.indicator.checkbox"))
              .selected(initialCheckboxValue)
              .onChanged {
                val newCheckboxValue = it.isSelected

                statusBarWidgetSettings.setEnabled(memoryWidgetFactory, newCheckboxValue)
                ProjectManager.getInstance().openProjects.forEach { project ->
                  project.service<StatusBarWidgetsManager>().updateWidget(memoryWidgetFactory)
                }
              }
          }.rowComment(PyBundle.message("settings.memory.indicator.row.comment"))
        }
      }
    }
  }

  override fun isModified(): Boolean {
    return initialHeapSize.toString() != newHeapSize
  }

  override fun apply() {
    super.apply()
    if (isModified) {
      saveNewHeapSize()
      RestartDialogImpl.showRestartRequired()
    }
  }

  override fun reset() {
    super.reset()
    newHeapSize = initialHeapSize.toString()
    newHeapSizeField.text = newHeapSize
  }

  private fun getInitialHeapSize(): Int {
    return VMOptions.readOption(option, true)
  }

  @Throws(ConfigurationException::class)
  private fun saveNewHeapSize(): Boolean {
    try {
      val newSize = newHeapSize.toInt()
      EditMemorySettingsService.getInstance().save(option, newSize)
      return true
    }
    catch (e: IOException) {
      throw ConfigurationException(/* message = */ e.message,
                                   /* title = */ OptionsBundle.message("cannot.save.settings.default.dialog.title"))
    }
  }

  private fun getSuggestedValue(): Int {
    val maxSuggestedHeapSizeFromRegistry = Registry.intValue("max.suggested.heap.size")

    return if (initialHeapSize > 0) {
      initialHeapSize + HEAP_INCREMENT
    }
    else {
      maxSuggestedHeapSizeFromRegistry
    }
  }

  companion object {
    private const val MEMORY_USAGE_SETTINGS_ID = "pycharm.memory.usage.settings"
    private const val MEMORY_USAGE_SETTINGS_HELP_TOPIC = "Increasing_Memory_Heap"
  }
}