package org.intellij.plugins.markdown.settings

import com.intellij.openapi.components.*
import org.intellij.plugins.markdown.MarkdownBundle
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

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
    var listNumberingType by enum(ListNumberingType.SEQUENTIAL)
    var enableFileDrop: Boolean by property(true)
  }

  enum class ListNumberingType(@Nls private val text: String) {
    SEQUENTIAL("markdown.smart.keys.configurable.lists.numbering.sequential"),
    ONES("markdown.smart.keys.configurable.lists.numbering.all.ones"),
    PREVIOUS_NUMBER("markdown.smart.keys.configurable.lists.numbering.previous.number");

    override fun toString(): String = MarkdownBundle.message(text)
  }

  override fun noStateLoaded() {
    reset()
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
