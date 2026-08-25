// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.markdown.jcef.extensions

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.getOrLogException
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.extensions.MarkdownBrowserPreviewExtension
import org.intellij.plugins.markdown.extensions.MarkdownBrowserPreviewExtension.Provider.Companion.OPEN_LINK_EVENT_NAME
import org.intellij.plugins.markdown.ui.preview.BrowserPipe
import org.intellij.plugins.markdown.ui.preview.MarkdownHtmlPanel
import org.intellij.plugins.markdown.ui.preview.MarkdownHtmlPanelEx
import org.intellij.plugins.markdown.ui.preview.PreviewClickConfirmation
import org.intellij.plugins.markdown.ui.preview.ResourceProvider
import org.intellij.plugins.markdown.ui.preview.accessor.MarkdownLinkOpener
import org.intellij.plugins.markdown.ui.preview.accessor.MarkdownSourceLinkNavigator

internal class ProcessLinksExtension(private val panel: MarkdownHtmlPanel) : MarkdownBrowserPreviewExtension, ResourceProvider {
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

  private fun openLink(panel: MarkdownHtmlPanel, data: String): Boolean {
    if (!Registry.`is`("markdown.open.link.in.external.browser")) return true
    val (needsConfirmation, link) = PreviewClickConfirmation.parseFlagPrefixed(data) ?: return true
    // http, https and `source://` all skip the confirmation MarkdownLinkOpener would otherwise show,
    // so a link stretched invisibly over the preview has no other place left to be caught.
    if (!needsConfirmation) {
      return followLink(panel, link)
    }
    ApplicationManager.getApplication().invokeLater {
      if (confirmFollowLink(panel, link)) {
        followLink(panel, link)
      }
    }
    return false
  }

  private fun followLink(panel: MarkdownHtmlPanel, link: String): Boolean {
    if (MarkdownSourceLinkNavigator.navigate(panel.project, link, panel.virtualFile)) return true
    return openExternalLink(panel, link)
  }

  private fun confirmFollowLink(panel: MarkdownHtmlPanel, link: String): Boolean {
    return MessageDialogBuilder
      .yesNo(
        MarkdownBundle.message("markdown.preview.follow.link.confirm.title"),
        MarkdownBundle.message("markdown.preview.follow.link.confirm.message", link)
      )
      .icon(Messages.getWarningIcon())
      .yesText(MarkdownBundle.message("markdown.preview.follow.link.confirm.follow"))
      .noText(Messages.getCancelButton())
      .ask(panel.project)
  }

  private fun openExternalLink(panel: MarkdownHtmlPanel, link: String): Boolean {
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

  class Provider : MarkdownBrowserPreviewExtension.Provider {
    override fun createBrowserExtension(panel: MarkdownHtmlPanel): MarkdownBrowserPreviewExtension {
      return ProcessLinksExtension(panel)
    }
  }
}
