package org.intellij.plugins.markdown.settings

import com.intellij.openapi.components.*
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@State(name = "MarkdownCodeFoldingSettings", storages = [Storage("editor.xml")], category = SettingsCategory.CODE)
class MarkdownCodeFoldingSettings: SimplePersistentStateComponent<MarkdownCodeFoldingSettings.State>(State()) {
  class State: BaseState() {
    var collapseLinks: Boolean by property(true)
    var collapseFrontMatter: Boolean by property(true)
    var collapseTables: Boolean by property(false)
    var collapseCodeFences: Boolean by property(false)
    var collapseTableOfContents: Boolean by property(true)
  }

  fun reset() {
    loadState(State())
  }

  companion object {
    @JvmStatic
    fun getInstance(): MarkdownCodeFoldingSettings {
      return service()
    }
  }
}

