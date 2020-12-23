// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.extensions.common

import org.intellij.plugins.markdown.extensions.MarkdownBrowserPreviewExtension
import org.intellij.plugins.markdown.extensions.jcef.MarkdownJCEFPreviewExtension
import org.intellij.plugins.markdown.settings.MarkdownApplicationSettings
import org.intellij.plugins.markdown.settings.MarkdownFontUtil
import org.intellij.plugins.markdown.ui.preview.ResourceProvider

internal class FontStylesExtension : MarkdownJCEFPreviewExtension, ResourceProvider {
  val cssSettings get() = MarkdownApplicationSettings.getInstance().markdownCssSettings

  override val priority: MarkdownBrowserPreviewExtension.Priority
    get() = MarkdownBrowserPreviewExtension.Priority.AFTER_ALL
  override val styles: List<String> = listOf("font/font-settings.css")
  override val resourceProvider: ResourceProvider = this
  override fun canProvide(resourceName: String): Boolean = resourceName in styles
  override fun loadResource(resourceName: String): ResourceProvider.Resource {
    return ResourceProvider.Resource(MarkdownFontUtil.getFontSizeCss(cssSettings.fontSize, cssSettings.fontFamily).toByteArray())
  }
}
