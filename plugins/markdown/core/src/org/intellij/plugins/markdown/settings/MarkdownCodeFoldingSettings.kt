package org.intellij.plugins.markdown.settings

import com.intellij.openapi.components.*

@Service(Service.Level.APP)
@State(name = "MarkdownCodeFoldingSettings", storages = [Storage("editor.xml")])
internal class MarkdownCodeFoldingSettings: SimplePersistentStateComponent<MarkdownCodeFoldingSettings.State>(State()) {
  data class State(
    val collapseLinks: Boolean = true,
    val collapseFrontMatter: Boolean = true
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

