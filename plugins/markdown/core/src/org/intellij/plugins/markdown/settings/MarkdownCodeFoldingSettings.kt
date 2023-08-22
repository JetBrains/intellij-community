package org.intellij.plugins.markdown.settings

import com.intellij.openapi.components.*
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Service(Service.Level.APP)
@State(name = "MarkdownCodeFoldingSettings", storages = [Storage("editor.xml")])
class MarkdownCodeFoldingSettings: SimplePersistentStateComponent<MarkdownCodeFoldingSettings.State>(State()) {
  data class State(
    val collapseLinks: Boolean = true,
    val collapseFrontMatter: Boolean = true,
    val collapseTables: Boolean = false,
    val collapseCodeFences: Boolean = false,
    val collapseTableOfContents: Boolean = true
  ): BaseState()

  fun update(transform: (State) -> State) {
    this.loadState(transform(this.state))
  }

  companion object {
    @JvmStatic
    fun getInstance(): MarkdownCodeFoldingSettings {
      return service()
    }
  }
}

