// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.extensions.jcef

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.diagnostic.getOrLogException
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.jcef.JBCefPsiNavigationUtils
import org.intellij.plugins.markdown.extensions.MarkdownBrowserPreviewExtension
import org.intellij.plugins.markdown.extensions.MarkdownBrowserPreviewExtension.Provider.Companion.OPEN_LINK_EVENT_NAME
import org.intellij.plugins.markdown.ui.preview.BrowserPipe
import org.intellij.plugins.markdown.ui.preview.MarkdownHtmlPanel
import org.intellij.plugins.markdown.ui.preview.MarkdownHtmlPanelEx
import org.intellij.plugins.markdown.ui.preview.ResourceProvider
import org.intellij.plugins.markdown.ui.preview.accessor.MarkdownLinkOpener

internal class ProcessLinksExtension(private val panel: MarkdownHtmlPanel): MarkdownBrowserPreviewExtension, ResourceProvider {
  private val handler = object : BrowserPipe.Handler {
    override fun processMessageReceived(data: String): Boolean {
      return openLink(panel, data)
    }
  }

  init {
    panel.browserPipe?.subscribe(OPEN_LINK_EVENT_NAME, handler)
    Disposer.register(this) {
      panel.browserPipe?.removeSubscription(OPEN_LINK_EVENT_NAME, handler)
    }
  }

  private fun openLink(panel: MarkdownHtmlPanel, link: String): Boolean {
    if (!Registry.`is`("markdown.open.link.in.external.browser")) return true
    if (JBCefPsiNavigationUtils.navigateTo(link)) return true

    if (panel is MarkdownHtmlPanelEx) {
      if (panel.getUserData(MarkdownHtmlPanelEx.DO_NOT_USE_LINK_OPENER) == true) {
        runCatching {
          BrowserUtil.browse(link)
        }.getOrLogException(thisLogger())
        return false
      }
    }
    if (Registry.`is`("markdown.open.link.fallback"))
      MarkdownLinkOpener.getInstance().openLink(panel.project, link)
    else
      MarkdownLinkOpener.getInstance().openLink(panel.project, link, panel.virtualFile)
    return false
  }

  override val scripts: List<String> = listOf("processLinks/processLinks.js")

  override val resourceProvider: ResourceProvider = this

  override fun canProvide(resourceName: String): Boolean = resourceName in scripts

  override fun loadResource(resourceName: String): ResourceProvider.Resource? {
    return ResourceProvider.loadInternalResource(this::class, resourceName)
  }

  override fun dispose() = Unit

  class Provider: MarkdownBrowserPreviewExtension.Provider {
    override fun createBrowserExtension(panel: MarkdownHtmlPanel): MarkdownBrowserPreviewExtension {
      return ProcessLinksExtension(panel)
    }
  }
}
