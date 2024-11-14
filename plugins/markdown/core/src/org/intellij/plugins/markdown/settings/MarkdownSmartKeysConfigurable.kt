package org.intellij.plugins.markdown.settings

import com.intellij.application.options.CodeCompletionOptionsCustomSection
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.application.readAction
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.options.UiDslUnnamedConfigurable
import com.intellij.ui.CollectionComboBoxModel
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.bindSelected
import kotlinx.coroutines.launch
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.util.MarkdownApplicationScope

internal class MarkdownSmartKeysConfigurable: UiDslUnnamedConfigurable.Simple(), SearchableConfigurable, CodeCompletionOptionsCustomSection {
  private val settings
    get() = MarkdownCodeInsightSettings.getInstance()

  private val coroutineScope
    get() = MarkdownApplicationScope.scope()

  override fun getDisplayName(): String {
    return MarkdownBundle.message("markdown.smart.keys.configurable.name")
  }

  override fun getId(): String {
    return ID
  }

  override fun Panel.createContent() {
    useNewComboBoxRenderer()

    group(title = MarkdownBundle.message("markdown.smart.keys.configurable.tables.group.name")) {
      row {
        checkBox(MarkdownBundle.message("markdown.smart.keys.configurable.tables.reformat.on.type"))
          .bindSelected(
            getter = { settings.state.reformatTablesOnType },
            setter = { settings.state.reformatTablesOnType = it }
          )
      }
      row {
        checkBox(MarkdownBundle.message("markdown.smart.keys.configurable.tables.insert.html.line.break"))
          .bindSelected(
            getter = { settings.state.insertHtmlLineBreakInsideTables },
            setter = { settings.state.insertHtmlLineBreakInsideTables = it }
          )
      }
      row {
        checkBox(MarkdownBundle.message("markdown.smart.keys.configurable.tables.insert.new.row"))
          .bindSelected(
            getter = { settings.state.insertNewTableRowOnShiftEnter },
            setter = { settings.state.insertNewTableRowOnShiftEnter = it }
          )
      }
      row {
        checkBox(MarkdownBundle.message("markdown.smart.keys.configurable.tables.use.cell.navigation"))
          .bindSelected(
            getter = { settings.state.useTableCellNavigation },
            setter = { settings.state.useTableCellNavigation = it }
          )
      }
    }
    group(title = MarkdownBundle.message("markdown.smart.keys.configurable.lists.group.name")) {
      row {
        checkBox(MarkdownBundle.message("markdown.smart.keys.configurable.lists.adjust.indentation.on.type"))
          .bindSelected(
            getter = { settings.state.adjustListIndentation },
            setter = { settings.state.adjustListIndentation = it }
          )
      }
      row {
        checkBox(MarkdownBundle.message("markdown.smart.keys.configurable.lists.smart.enter.backspace"))
          .bindSelected(
            getter = { settings.state.smartEnterAndBackspace },
            setter = { settings.state.smartEnterAndBackspace = it }
          )
      }
      var renumberCheckBox: JBCheckBox? = null
      row {
        renumberCheckBox = checkBox(MarkdownBundle.message("markdown.smart.keys.configurable.lists.renumber.lists"))
          .bindSelected(
            getter = { settings.state.renumberListsOnType },
            setter = { settings.state.renumberListsOnType = it }
          )
          .applyToComponent { isEnabled = settings.state.listNumberingType != MarkdownCodeInsightSettings.ListNumberingType.PREVIOUS_NUMBER }
          .component
      }
      row(MarkdownBundle.message("markdown.smart.keys.configurable.lists.numbering")) {
        comboBox(CollectionComboBoxModel(MarkdownCodeInsightSettings.ListNumberingType.entries))
          .bindItem(
            getter = { settings.state.listNumberingType },
            setter = {
              if (it != null) {
                settings.state.listNumberingType = it
              }
              coroutineScope.launch {
                readAction {
                  ProjectUtil.getOpenProjects().forEach { project ->
                    DaemonCodeAnalyzer.getInstance(project).restart()
                  }
                }
              }
            }
          )
          .onChanged {
            renumberCheckBox?.isEnabled = it.item != MarkdownCodeInsightSettings.ListNumberingType.PREVIOUS_NUMBER
          }
      }
    }
    group(title = MarkdownBundle.message("markdown.smart.keys.configurable.other.group.name")) {
      row {
        checkBox(MarkdownBundle.message("markdown.smart.keys.configurable.other.file.drop"))
          .bindSelected(
            getter = { settings.state.enableFileDrop },
            setter = { settings.state.enableFileDrop = it }
          )
      }
    }
  }

  companion object {
    internal const val ID = "Settings.Markdown.SmartKeys"
  }

  override fun getHelpTopic() = "reference.settings.editor.smart.keys.markdown"
}
