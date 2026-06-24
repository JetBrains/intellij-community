// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.webview.preview

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolder
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.util.coroutines.childScope
import com.intellij.ui.webview.api.WebViewAssetPath
import com.intellij.ui.webview.api.WebViewAssetRoot
import com.intellij.ui.webview.api.WebViewPanel
import com.intellij.ui.webview.api.WebViewPanelOptions
import com.intellij.ui.webview.api.createWebViewPanel
import com.intellij.util.asDisposable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.intellij.plugins.markdown.settings.MarkdownPreviewSettings
import org.intellij.plugins.markdown.ui.preview.MarkdownContentPanel
import org.intellij.plugins.markdown.ui.preview.MarkdownHtmlPanel
import org.intellij.plugins.markdown.ui.preview.accessor.MarkdownLinkOpener
import org.jetbrains.annotations.ApiStatus
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

private val LOG = logger<WebViewMarkdownPreviewPanel>()

class WebViewMarkdownPreviewPanel(
  private val project: Project?,
  private val virtualFile: VirtualFile?,
) : MarkdownContentPanel, UserDataHolder by UserDataHolderBase() {
  constructor() : this(project = null, virtualFile = null)

  private val coroutineScope = service<ScopeHolder>()
    .coroutineScope
    .childScope("Markdown WebView preview")

  private val rootComponent = JPanel(BorderLayout())

  @Volatile
  private var webViewPanel: WebViewPanel? = null

  @Volatile
  private var pageReady: Boolean = false

  @Volatile
  private var lastUpdate: MarkdownUpdate? = null

  init {
    ApplicationManager.getApplication().messageBus.connect(coroutineScope.asDisposable()).subscribe(
      MarkdownPreviewSettings.ChangeListener.TOPIC,
      MarkdownPreviewSettings.ChangeListener { sendContentUpdate() },
    )
    loadWebView()
  }

  override fun getComponent(): JComponent = rootComponent

  override fun setHtml(html: String, initialScrollOffset: Int, document: VirtualFile?) {
    LOG.error("WebView Markdown preview expects raw Markdown content through MarkdownContentPanel.setMarkdown(...); setHtml(...) should not be called")
  }

  override fun setHtml(html: String, initialScrollOffset: Int, initialScrollLineNumber: Int, document: VirtualFile?) {
    LOG.error("WebView Markdown preview expects raw Markdown content through MarkdownContentPanel.setMarkdown(...); setHtml(...) should not be called")
  }

  override fun setMarkdown(markdown: String, initialScrollOffset: Int, initialScrollLineNumber: Int, document: VirtualFile?) {
    val update = MarkdownUpdate(markdown, initialScrollLineNumber)
    lastUpdate = update
    sendContentUpdate(update)
  }

  override fun reloadWithOffset(offset: Int) {
    val update = lastUpdate ?: return
    val nextUpdate = update.copy(initialScrollLineNumber = lineNumberAtOffset(update.markdown, offset))
    lastUpdate = nextUpdate
    sendContentUpdate(nextUpdate)
  }

  override suspend fun scrollTo(editor: Editor, line: Int) {
    lastUpdate = lastUpdate?.copy(initialScrollLineNumber = line)
    val panel = webViewPanel ?: return
    if (!pageReady) return
    panel.interop.callable(MarkdownPreviewPageApi.ID).scrollToLine(MarkdownScrollToLineParams(line))
  }

  private fun loadWebView() {
    coroutineScope.launch {
      try {
        withContext(Dispatchers.EDT) {
          val panel = createWebViewPanel(
            scope = coroutineScope,
            options = WebViewPanelOptions(
              assetRoot = createAssetRoot(),
              debugName = "Markdown preview: ${project?.name ?: "unknown project"}",
            ),
          )
          panel.interop.implement(MarkdownPreviewHostApi.ID, createHostApi())
          webViewPanel = panel
          rootComponent.add(panel.component, BorderLayout.CENTER)
          rootComponent.revalidate()
          rootComponent.repaint()
          panel.reload()
        }
      }
      catch (e: CancellationException) {
        throw e
      }
      catch (t: Throwable) {
        LOG.warn("Failed to load Markdown WebView preview", t)
      }
    }
  }

  private fun createAssetRoot(): WebViewAssetRoot {
    var assetRoot = ASSET_ROOT.withScopedAssetProvider(WebViewAssetPath.of(MARKDOWN_ICON_PREFIX), MarkdownPreviewIconProvider())
    val project = project
    if (project != null) {
      assetRoot = assetRoot.withScopedAssetProvider(
        WebViewAssetPath.of(MARKDOWN_RESOURCE_PREFIX),
        MarkdownPreviewResourceProvider(project, virtualFile),
      )
    }
    return assetRoot
  }

  private fun createHostApi(): MarkdownPreviewHostApi {
    return object : MarkdownPreviewHostApi {
      override suspend fun pageReady() {
        pageReady = true
        sendContentUpdate()
      }

      override suspend fun openLink(params: MarkdownOpenLinkParams) {
        MarkdownLinkOpener.getInstance().openLink(project, params.href, virtualFile)
      }

      override suspend fun runCommand(params: MarkdownRunCommandParams) {
      }
    }
  }

  private fun sendContentUpdate(update: MarkdownUpdate? = lastUpdate) {
    if (!pageReady) return
    val panel = webViewPanel ?: return
    val actualUpdate = update ?: return
    coroutineScope.launch {
      try {
        panel.interop.callable(MarkdownPreviewPageApi.ID).contentChanged(
          MarkdownContentChangedParams(
            markdown = actualUpdate.markdown,
            scrollLine = actualUpdate.initialScrollLineNumber,
            settings = currentPreviewSettings(),
          )
        )
      }
      catch (e: CancellationException) {
        throw e
      }
      catch (t: Throwable) {
        LOG.warn("Failed to update Markdown WebView preview content", t)
      }
    }
  }

  private fun currentPreviewSettings(): MarkdownPreviewSettingsParams {
    val fontSize = service<MarkdownPreviewSettings>().state.fontSize
    val defaultFontSize = MarkdownPreviewSettings.State().fontSize
    return MarkdownPreviewSettingsParams(fontSize = fontSize.takeIf { it != defaultFontSize })
  }

  override fun addScrollListener(listener: MarkdownHtmlPanel.ScrollListener) {
  }

  override fun removeScrollListener(listener: MarkdownHtmlPanel.ScrollListener) {
  }

  override fun scrollBy(horizontalUnits: Int, verticalUnits: Int) {
  }

  @ApiStatus.Experimental
  override fun getProject(): Project? = project

  @ApiStatus.Experimental
  override fun getVirtualFile(): VirtualFile? = virtualFile

  override fun dispose() {
    coroutineScope.cancel()
  }

  private data class MarkdownUpdate(
    val markdown: String,
    val initialScrollLineNumber: Int,
  )

  @Service(Service.Level.APP)
  private class ScopeHolder(
    val coroutineScope: CoroutineScope,
  )

  private companion object {
    private val ASSET_ROOT = WebViewAssetRoot.forView("markdown-preview")

    private fun lineNumberAtOffset(text: String, offset: Int): Int {
      val targetOffset = offset.coerceIn(0, text.length)
      var line = 0
      for (index in 0 until targetOffset) {
        if (text[index] == '\n') line++
      }
      return line
    }
  }
}
