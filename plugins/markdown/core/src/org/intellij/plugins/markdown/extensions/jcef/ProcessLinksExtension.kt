// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.extensions.jcef

import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.jcef.JBCefPsiNavigationUtils
import org.intellij.plugins.markdown.extensions.MarkdownBrowserPreviewExtension
import org.intellij.plugins.markdown.ui.preview.MarkdownHtmlPanel
import org.intellij.plugins.markdown.ui.preview.ResourceProvider
import org.intellij.plugins.markdown.ui.preview.accessor.MarkdownLinkOpener

internal class ProcessLinksExtension(private val panel: MarkdownHtmlPanel): MarkdownBrowserPreviewExtension, ResourceProvider {
  init {
    panel.browserPipe?.subscribe(openLinkEventName, ::openLink)
    Disposer.register(this) {
      panel.browserPipe?.removeSubscription(openLinkEventName, ::openLink)
    }
  }

  private fun openLink(link: String) {
    if (!Registry.`is`("markdown.open.link.in.external.browser")) {
      return
    }
    if (JBCefPsiNavigationUtils.navigateTo(link)) {
      return
    }
    MarkdownLinkOpener.getInstance().openLink(panel.project, link)
  }

  override val scripts: List<String> = listOf("processLinks/processLinks.js")

  override val resourceProvider: ResourceProvider = this

  override fun canProvide(resourceName: String): Boolean = resourceName in scripts

  override fun loadResource(resourceName: String): ResourceProvider.Resource? {
    return ResourceProvider.loadInternalResource(this::class, resourceName)
  }

  override fun dispose() = Unit

  class Provider: MarkdownBrowserPreviewExtension.Provider {
    override fun createBrowserExtension(panel: MarkdownHtmlPanel): MarkdownBrowserPreviewExtension? {
      return ProcessLinksExtension(panel)
    }
  }

  companion object {
    private const val openLinkEventName = "openLink"
  }
}
