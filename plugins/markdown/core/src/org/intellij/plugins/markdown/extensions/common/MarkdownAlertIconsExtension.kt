// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.extensions.common

import org.intellij.plugins.markdown.extensions.MarkdownBrowserPreviewExtension
import org.intellij.plugins.markdown.extensions.MarkdownExtensionsUtil
import org.intellij.plugins.markdown.ui.preview.MarkdownHtmlPanel
import org.intellij.plugins.markdown.ui.preview.ResourceProvider
import org.intellij.plugins.markdown.ui.preview.html.MarkdownAlertIcons

internal class MarkdownAlertIconsExtension : MarkdownBrowserPreviewExtension, ResourceProvider {
  override val priority: MarkdownBrowserPreviewExtension.Priority
    get() = MarkdownBrowserPreviewExtension.Priority.BEFORE_ALL

  override val resourceProvider: ResourceProvider
    get() = this

  override fun canProvide(resourceName: String): Boolean = resourceName in MarkdownAlertIcons.resources

  override fun loadResource(resourceName: String): ResourceProvider.Resource? {
    val icon = MarkdownAlertIcons.resources[resourceName] ?: return null
    val format = resourceName.substringAfterLast('.')
    return ResourceProvider.Resource(MarkdownExtensionsUtil.loadIcon(icon, format))
  }

  override fun dispose() = Unit

  class Provider : MarkdownBrowserPreviewExtension.Provider {
    override fun createBrowserExtension(panel: MarkdownHtmlPanel): MarkdownBrowserPreviewExtension {
      return MarkdownAlertIconsExtension()
    }
  }
}
