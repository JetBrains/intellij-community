package org.intellij.plugins.markdown.settings

import com.intellij.openapi.components.*
import com.intellij.util.xmlb.annotations.Property
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
@Service
@State(name = "MarkdownCodeInsightSettings", storages = [(Storage("markdown.xml"))])
class MarkdownCodeInsightSettings: PersistentStateComponent<MarkdownCodeInsightSettings.State> {
  private var state = State()

  data class State(
    @Property
    val reformatTablesOnType: Boolean = true,
    @Property
    val insertHtmlLineBreakInsideTables: Boolean = true,
    @Property
    val insertNewTableRowOnShiftEnter: Boolean = true,
    @Property
    val useTableCellNavigation: Boolean = true,
    @Property
    val adjustListIndentation: Boolean = true,
    @Property
    val indentListsOnTab: Boolean = true,
    @Property
    val smartEnterAndBackspace: Boolean = true,
    @Property
    val renumberListsOnType: Boolean = false,
    @Property
    val enableFilesDrop: Boolean = true
  )

  fun update(transform: (State) -> State) {
    loadState(transform(state))
  }

  override fun getState(): State {
    return state
  }

  override fun loadState(state: State) {
    this.state = state
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
