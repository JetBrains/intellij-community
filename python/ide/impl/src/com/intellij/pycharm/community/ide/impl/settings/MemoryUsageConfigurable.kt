package com.intellij.pycharm.community.ide.impl.settings

import com.intellij.diagnostic.EditMemorySettingsService
import com.intellij.diagnostic.VMOptions
import com.intellij.openapi.components.service
import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.options.OptionsBundle
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.openapi.wm.impl.status.MemoryUsagePanel
import com.intellij.openapi.wm.impl.status.widget.StatusBarWidgetSettings
import com.intellij.openapi.wm.impl.status.widget.StatusBarWidgetsManager
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.*
import com.intellij.util.ui.RestartDialogImpl
import com.jetbrains.python.PyBundle
import java.io.IOException

class MemoryUsageConfigurable : BoundSearchableConfigurable(
  PyBundle.message("settings.memory.group.title"),
  helpTopic = MEMORY_USAGE_SETTINGS_HELP_TOPIC,
  _id = MEMORY_USAGE_SETTINGS_ID) {

  private val UNKNOWN_MEMORY_VALUE = -1
  private val MIN_VALUE: Int = 256
  private val MAX_VALUE: Int = 1_000_000
  private val option = VMOptions.MemoryKind.HEAP

  private val initialHeapSize: Int = getInitialHeapSize()
  private var newHeapSize: String = initialHeapSize.toString()
  private lateinit var newHeapSizeField: JBTextField

  private var memoryWidgetCheckbox: JBCheckBox? = null
  private var initialMemoryWidgetValue: Boolean? = null

  override fun createPanel(): DialogPanel {
    return panel {
      row(PyBundle.message("settings.memory.heap.size.label")) {
        newHeapSizeField = intTextField(IntRange(MIN_VALUE, MAX_VALUE))
          .text(initialHeapSize.toString())
          .columns(7)
          .gap(RightGap.SMALL)
          .focused()
          .onChanged {
            newHeapSize = it.text
          }
          .component

      }
        .topGap(TopGap.MEDIUM)
        .rowComment(PyBundle.message("settings.memory.heap.size.row.comment"))

      row {
        comment(PyBundle.message("settings.memory.heap.size.restarting.warn.label", "AllIcons.General.Warning"))
      }

      val statusBarWidgetSettings = StatusBarWidgetSettings.getInstance()
      val memoryWidgetFactory: StatusBarWidgetFactory? = StatusBarWidgetFactory.EP_NAME.extensionList.find { it.id == MemoryUsagePanel.WIDGET_ID }
      if (memoryWidgetFactory != null) {
        initialMemoryWidgetValue = StatusBarWidgetSettings.getInstance().isEnabled(memoryWidgetFactory)
        row {
          @Suppress("DialogTitleCapitalization")
          memoryWidgetCheckbox = checkBox(PyBundle.message("settings.memory.indicator.checkbox"))
            .selected(initialMemoryWidgetValue ?: false)
            .onChanged {
              val newMemoryWidgetValue = it.isSelected

              statusBarWidgetSettings.setEnabled(memoryWidgetFactory, newMemoryWidgetValue)
              ProjectManager.getInstance().openProjects.forEach { project ->
                project.service<StatusBarWidgetsManager>().updateWidget(memoryWidgetFactory)
              }
            }
            .component
        }
          .topGap(TopGap.MEDIUM)
          .rowComment(PyBundle.message("settings.memory.indicator.row.comment"))
      }
    }
  }

  override fun isModified(): Boolean {
    val memoryWidgetModified = initialMemoryWidgetValue?.let { initial ->
      initial != memoryWidgetCheckbox?.isSelected
    } ?: false

    return memoryWidgetModified || initialHeapSize.toString() != newHeapSize
  }

  override fun apply() {
    super.apply()
    if (isModified && !isInDebugMode()) {
      saveNewHeapSize()
      RestartDialogImpl.showRestartRequired()
    }
  }

  override fun reset() {
    super.reset()
    newHeapSize = initialHeapSize.toString()
    newHeapSizeField.text = newHeapSize
    memoryWidgetCheckbox?.isSelected = initialMemoryWidgetValue ?: false
  }

  @Throws(ConfigurationException::class)
  private fun saveNewHeapSize() {
    if (isInDebugMode()) return

    try {
      val newSize = newHeapSize.toInt()
      EditMemorySettingsService.getInstance().save(option, newSize)
    }
    catch (e: IOException) {
      throw ConfigurationException(/* message = */ e.message,
                                   /* title = */ OptionsBundle.message("cannot.save.settings.default.dialog.title"))
    }
  }

  private fun getInitialHeapSize(): Int {
    if (isInDebugMode()) return UNKNOWN_MEMORY_VALUE

    return VMOptions.readOption(option, true)
  }

  private fun isInDebugMode(): Boolean {
    return !VMOptions.canWriteOptions()
  }

  companion object {
    private const val MEMORY_USAGE_SETTINGS_ID = "pycharm.memory.usage.settings"
    private const val MEMORY_USAGE_SETTINGS_HELP_TOPIC = "Increasing_Memory_Heap"
  }
}