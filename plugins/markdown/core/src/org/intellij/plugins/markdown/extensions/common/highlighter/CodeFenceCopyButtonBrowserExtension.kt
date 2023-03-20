// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.extensions.common.highlighter

import com.intellij.icons.AllIcons
import com.intellij.lang.LangBundle
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.wm.WindowManager
import org.intellij.plugins.markdown.extensions.MarkdownBrowserPreviewExtension
import org.intellij.plugins.markdown.extensions.MarkdownExtensionsUtil
import org.intellij.plugins.markdown.ui.preview.BrowserPipe
import org.intellij.plugins.markdown.ui.preview.MarkdownHtmlPanel
import org.intellij.plugins.markdown.ui.preview.ResourceProvider
import org.intellij.plugins.markdown.ui.preview.html.PreviewEncodingUtil
import java.awt.datatransfer.StringSelection
import javax.swing.Icon

internal class CodeFenceCopyButtonBrowserExtension(panel: MarkdownHtmlPanel, browserPipe: BrowserPipe): MarkdownBrowserPreviewExtension, ResourceProvider {
  init {
    browserPipe.subscribe("copy-button/copy") {
      val content = PreviewEncodingUtil.decodeContent(it)
      CopyPasteManager.getInstance().setContents(StringSelection(content))
      val project = panel.project ?: return@subscribe
      invokeLater {
        val statusBar = WindowManager.getInstance().getStatusBar(project)
        val text = StringUtil.shortenTextWithEllipsis(content, 32, 0)
        statusBar?.info = LangBundle.message("status.bar.text.reference.has.been.copied", "'$text'")
      }
    }
  }

  override val resourceProvider = this

  override val styles = listOf(STYLE_NAME)

  override val scripts = listOf(SCRIPT_NAME)

  override fun canProvide(resourceName: String): Boolean {
    return resourceName in resources
  }

  override fun loadResource(resourceName: String): ResourceProvider.Resource? {
    return when (resourceName) {
      STYLE_NAME -> ResourceProvider.loadInternalResource<CodeFenceCopyButtonBrowserExtension>(STYLE_NAME)
      SCRIPT_NAME -> ResourceProvider.loadInternalResource<CodeFenceCopyButtonBrowserExtension>(SCRIPT_NAME)
      COPY_ICON_NAME -> loadIconAsResource(AllIcons.General.InlineCopy)
      COPY_ICON_HOVERED_NAME -> loadIconAsResource(AllIcons.General.InlineCopyHover)
      else -> null
    }
  }

  override fun dispose() = Unit

  class Provider: MarkdownBrowserPreviewExtension.Provider {
    override fun createBrowserExtension(panel: MarkdownHtmlPanel): MarkdownBrowserPreviewExtension? {
      return panel.browserPipe?.let { CodeFenceCopyButtonBrowserExtension(panel, it) }
    }
  }

  companion object {
    private const val STYLE_NAME = "copy-button.css"
    private const val SCRIPT_NAME = "copy-button.js"
    private const val COPY_ICON_NAME = "copy-button-copy-icon.png"
    private const val COPY_ICON_HOVERED_NAME = "copy-button-copy-icon-hovered.png"

    private val resources = listOf(
      STYLE_NAME,
      SCRIPT_NAME,
      COPY_ICON_NAME,
      COPY_ICON_HOVERED_NAME
    )

    private fun loadIconAsResource(icon: Icon): ResourceProvider.Resource {
      return ResourceProvider.Resource(MarkdownExtensionsUtil.loadIcon(icon, "png"))
    }
  }
}
