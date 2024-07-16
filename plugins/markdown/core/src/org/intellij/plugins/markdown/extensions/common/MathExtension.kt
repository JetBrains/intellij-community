package org.intellij.plugins.markdown.extensions.common

import org.intellij.plugins.markdown.extensions.MarkdownBrowserPreviewExtension
import org.intellij.plugins.markdown.ui.preview.MarkdownHtmlPanel
import org.intellij.plugins.markdown.ui.preview.ResourceProvider

internal class MathExtension : MarkdownBrowserPreviewExtension, ResourceProvider {

  override val scripts: List<String> = listOf("tex-svg.js", "mathjax-render.js")

  override val resourceProvider: ResourceProvider = this

  override fun canProvide(resourceName: String): Boolean {
    return resourceName in scripts
  }
  override fun loadResource(resourceName: String): ResourceProvider.Resource? {
    return ResourceProvider.loadInternalResource<MathExtension>(resourceName)
  }

  override fun dispose() = Unit

  class Provider: MarkdownBrowserPreviewExtension.Provider {
    override fun createBrowserExtension(panel: MarkdownHtmlPanel): MarkdownBrowserPreviewExtension {
      return MathExtension()
    }
  }
}
