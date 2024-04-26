package org.intellij.plugins.markdown.settings

import com.intellij.application.options.CodeCompletionOptionsCustomSection
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.options.UiDslUnnamedConfigurable
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.bindSelected
import org.intellij.plugins.markdown.MarkdownBundle

internal class MarkdownSmartKeysConfigurable: UiDslUnnamedConfigurable.Simple(), SearchableConfigurable, CodeCompletionOptionsCustomSection {
  private val settings
    get() = MarkdownCodeInsightSettings.getInstance()

  override fun getDisplayName(): String {
    return MarkdownBundle.message("markdown.smart.keys.configurable.name")
  }

  override fun getId(): String {
    return ID
  }

  override fun Panel.createContent() {
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
      row {
        checkBox(MarkdownBundle.message("markdown.smart.keys.configurable.lists.renumber.lists"))
          .bindSelected(
            getter = { settings.state.renumberListsOnType },
            setter = { settings.state.renumberListsOnType = it }
          )
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
