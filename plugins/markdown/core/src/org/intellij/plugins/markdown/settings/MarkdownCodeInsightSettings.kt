package org.intellij.plugins.markdown.settings

import com.intellij.openapi.components.*
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
@State(name = "MarkdownCodeInsightSettings",
       category = SettingsCategory.CODE,
       storages = [(Storage("markdown.xml"))])
class MarkdownCodeInsightSettings: SimplePersistentStateComponent<MarkdownCodeInsightSettings.State>(State()) {
  class State: BaseState() {
    var reformatTablesOnType: Boolean by property(true)
    var insertHtmlLineBreakInsideTables: Boolean by property(true)
    var insertNewTableRowOnShiftEnter: Boolean by property(true)
    var useTableCellNavigation: Boolean by property(true)
    var adjustListIndentation: Boolean by property(true)
    var smartEnterAndBackspace: Boolean by property(true)
    var renumberListsOnType: Boolean by property(false)
    var enableFileDrop: Boolean by property(true)
  }

  fun reset() {
    loadState(State())
  }

  companion object {
    @JvmStatic
    fun getInstance(): MarkdownCodeInsightSettings {
      return service()
    }
  }
}
