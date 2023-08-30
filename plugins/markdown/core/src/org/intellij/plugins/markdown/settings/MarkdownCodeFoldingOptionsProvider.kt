package org.intellij.plugins.markdown.settings

import com.intellij.application.options.editor.CodeFoldingOptionsProvider
import com.intellij.openapi.options.BeanConfigurable
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.lang.MarkdownLanguage

internal class MarkdownCodeFoldingOptionsProvider: BeanConfigurable<MarkdownCodeFoldingSettings>(
  MarkdownCodeFoldingSettings.getInstance(),
  MarkdownLanguage.INSTANCE.displayName
), CodeFoldingOptionsProvider {
  private val settings
    get() = MarkdownCodeFoldingSettings.getInstance()

  init {
    checkBox(
      MarkdownBundle.message("markdown.code.folding.configurable.collapse.front.matter"),
      { settings.state.collapseFrontMatter },
      { value -> settings.state.collapseFrontMatter = value }
    )
    checkBox(
      MarkdownBundle.message("markdown.code.folding.configurable.collapse.links"),
      { settings.state.collapseLinks },
      { value -> settings.state.collapseLinks = value }
    )
    checkBox(
      MarkdownBundle.message("markdown.code.folding.configurable.collapse.tables"),
      { settings.state.collapseTables },
      { value -> settings.state.collapseTables = value }
    )
    checkBox(
      MarkdownBundle.message("markdown.code.folding.configurable.collapse.code.fences"),
      { settings.state.collapseCodeFences },
      { value -> settings.state.collapseCodeFences = value }
    )
    checkBox(
      MarkdownBundle.message("markdown.code.folding.configurable.collapse.table.of.contents"),
      { settings.state.collapseTableOfContents },
      { value -> settings.state.collapseTableOfContents = value }
    )
  }
}
