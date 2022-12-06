package org.intellij.plugins.markdown.settings

import com.intellij.application.options.CodeCompletionOptionsCustomSection
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.UiDslUnnamedConfigurable
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.bindSelected
import org.intellij.plugins.markdown.MarkdownBundle

internal class MarkdownSmartKeysConfigurable: UiDslUnnamedConfigurable.Simple(), Configurable, CodeCompletionOptionsCustomSection {
  private val settings
    get() = MarkdownCodeInsightSettings.getInstance()

  override fun getDisplayName(): String {
    return MarkdownBundle.message("markdown.smart.keys.configurable.name")
  }

  override fun Panel.createContent() {
    group(title = MarkdownBundle.message("markdown.smart.keys.configurable.tables.group.name")) {
      row {
        checkBox(MarkdownBundle.message("markdown.smart.keys.configurable.tables.reformat.on.type"))
          .bindSelected(
            getter = { settings.state.reformatTablesOnType },
            setter = { settings.update { state -> state.copy(reformatTablesOnType = it) } }
          )
      }
      row {
        checkBox(MarkdownBundle.message("markdown.smart.keys.configurable.tables.insert.html.line.break"))
          .bindSelected(
            getter = { settings.state.insertHtmlLineBreakInsideTables },
            setter = { settings.update { state -> state.copy(insertHtmlLineBreakInsideTables = it) } }
          )
      }
      row {
        checkBox(MarkdownBundle.message("markdown.smart.keys.configurable.tables.insert.new.row"))
          .bindSelected(
            getter = { settings.state.insertNewTableRowOnShiftEnter },
            setter = { settings.update { state -> state.copy(insertNewTableRowOnShiftEnter = it) } }
          )
      }
      row {
        checkBox(MarkdownBundle.message("markdown.smart.keys.configurable.tables.use.cell.navigation"))
          .bindSelected(
            getter = { settings.state.useTableCellNavigation },
            setter = { settings.update { state -> state.copy(useTableCellNavigation = it) } }
          )
      }
    }
    group(title = MarkdownBundle.message("markdown.smart.keys.configurable.lists.group.name")) {
      //row {
      //  checkBox(MarkdownBundle.message("markdown.smart.keys.configurable.lists.adjust.indentation.on.enter.backspace"))
      //    .bindSelected(
      //      getter = { settings.state.adjustListIndentation },
      //      setter = { settings.update { state -> state.copy(adjustListIndentation = it) } }
      //    )
      //}
      //row {
      //  checkBox(MarkdownBundle.message("markdown.smart.keys.configurable.lists.indent.on.tab"))
      //    .bindSelected(
      //      getter = { settings.state.indentListsOnTab },
      //      setter = { settings.update { state -> state.copy(indentListsOnTab = it) } }
      //    )
      //}
      row {
        checkBox(MarkdownBundle.message("markdown.smart.keys.configurable.lists.renumber.lists"))
          .bindSelected(
            getter = { settings.state.renumberListsOnType },
            setter = { settings.update { state -> state.copy(renumberListsOnType = it) } }
          )
      }
    }
    group(title = MarkdownBundle.message("markdown.smart.keys.configurable.other.group.name")) {
      row {
        checkBox(MarkdownBundle.message("markdown.smart.keys.configurable.other.file.drop"))
          .bindSelected(
            getter = { settings.state.enableFilesDrop },
            setter = { settings.update { state -> state.copy(enableFilesDrop = it) } }
          )
      }
    }
  }
}
