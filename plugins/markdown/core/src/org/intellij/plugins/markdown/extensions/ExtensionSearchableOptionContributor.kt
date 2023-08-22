package org.intellij.plugins.markdown.extensions

import com.intellij.ide.ui.search.SearchableOptionContributor
import com.intellij.ide.ui.search.SearchableOptionProcessor
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.settings.MarkdownSettingsConfigurable
import org.jetbrains.annotations.Nls

internal class ExtensionSearchableOptionContributor: SearchableOptionContributor() {
  override fun processOptions(processor: SearchableOptionProcessor) {
    val extensions = MarkdownExtensionsUtil.collectConfigurableExtensions(enabledOnly = false)
    for (extension in extensions) {
      processor.addExtensionOption(extension.displayName)
    }
  }

  companion object {
    private fun SearchableOptionProcessor.addExtensionOption(extensionDisplayName: @Nls String) {
      addOptions(
        extensionDisplayName,
        null,
        MarkdownBundle.message("markdown.settings.extension.enable.searchable.option.text", extensionDisplayName),
        MarkdownSettingsConfigurable.ID,
        MarkdownBundle.message("markdown.settings.name"),
        false
      )
    }
  }
}
