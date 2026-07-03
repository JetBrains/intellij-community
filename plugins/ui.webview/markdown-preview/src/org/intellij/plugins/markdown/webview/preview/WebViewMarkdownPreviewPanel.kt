// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.webview.preview

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
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
import com.intellij.ui.webview.api.WebViewIconSet
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
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.extensions.jcef.commandRunner.RunnerPlace
import org.intellij.plugins.markdown.settings.MarkdownPreviewSettings
import org.intellij.plugins.markdown.ui.preview.MarkdownContentPanel
import org.intellij.plugins.markdown.ui.preview.MarkdownHtmlPanel
import org.intellij.plugins.markdown.ui.preview.accessor.MarkdownLinkOpener
import org.jetbrains.annotations.ApiStatus
import java.awt.BorderLayout
import javax.swing.Icon
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

  private val pathLinkResolver = project?.let { MarkdownPreviewPathLinkResolver(it, coroutineScope) }

  private val rootComponent = JPanel(BorderLayout())

  @Volatile
  private var webViewPanel: WebViewPanel? = null

  @Volatile
  private var pageReady: Boolean = false

  @Volatile
  private var lastUpdate: MarkdownUpdate? = null

  @Volatile
  private var lastCommandSession: MarkdownRunCommandSession = MarkdownRunCommandSession.EMPTY

  @Volatile
  private var nextContentVersion: Int = 0

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
    val update = MarkdownUpdate(
      markdown = markdown,
      initialScrollLineNumber = initialScrollLineNumber,
      document = document ?: virtualFile,
      contentVersion = nextContentVersion++,
    )
    lastUpdate = update
    lastCommandSession = MarkdownRunCommandSession.EMPTY
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
    var assetRoot = ASSET_ROOT
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

      override suspend fun resolveRunCommands(params: MarkdownResolveRunCommandsParams): MarkdownResolvedRunCommandsParams {
        val session = resolveMarkdownRunCommands(params)
        return MarkdownResolvedRunCommandsParams(session.descriptors)
      }

      override suspend fun runCommand(params: MarkdownRunCommandParams) {
        runMarkdownCommand(params)
      }

      override suspend fun resolvePathLinks(params: MarkdownResolvePathLinksParams): MarkdownResolvedPathLinksParams {
        return MarkdownResolvedPathLinksParams(resolveMarkdownPathLinks(params))
      }

      override suspend fun navigatePathLink(params: MarkdownNavigatePathLinkParams) {
        navigateMarkdownPathLink(params)
      }
    }
  }

  private suspend fun resolveMarkdownPathLinks(params: MarkdownResolvePathLinksParams): List<String> {
    val update = lastUpdate ?: return emptyList()
    if (params.contentVersion != update.contentVersion) return emptyList()
    val resolver = pathLinkResolver ?: return emptyList()

    val resolvedRawPaths = LinkedHashSet<String>()
    for ((_, rawPath) in params.candidates.distinctBy { it.rawPath }) {
      if (resolver.resolve(rawPath, update.document).isNotEmpty()) {
        resolvedRawPaths.add(rawPath)
      }
    }
    return params.candidates.mapNotNull { candidate -> candidate.id.takeIf { candidate.rawPath in resolvedRawPaths } }
  }

  private suspend fun navigateMarkdownPathLink(params: MarkdownNavigatePathLinkParams) {
    val update = lastUpdate ?: return
    if (params.contentVersion != update.contentVersion) return
    val resolver = pathLinkResolver ?: return

    resolver.navigate(params.rawPath, update.document, rootComponent, params.clientX, params.clientY)
  }

  private fun resolveMarkdownRunCommands(params: MarkdownResolveRunCommandsParams): MarkdownRunCommandSession {
    val update = lastUpdate ?: return MarkdownRunCommandSession.EMPTY
    if (params.contentVersion != update.contentVersion) return MarkdownRunCommandSession.EMPTY

    val session = MarkdownRunCommandSession.resolve(project, update.document, params.candidates)
    lastCommandSession = session
    return session
  }

  private suspend fun runMarkdownCommand(params: MarkdownRunCommandParams) {
    val update = lastUpdate ?: return
    if (params.contentVersion != update.contentVersion) return

    val commandSession = lastCommandSession
    val command = commandSession.command(params.id) ?: run {
      LOG.warn("Markdown WebView preview command not found: ${params.id}")
      return
    }
    val targets = runCommandTargets(commandSession, command)
    if (targets.isEmpty()) return

    withContext(Dispatchers.EDT) {
      if (targets.size == 1) {
        targets.single().run()
      }
      else {
        showRunCommandPopup(targets, params)
      }
    }
  }

  private fun runCommandTargets(commandSession: MarkdownRunCommandSession, command: MarkdownRunCommand): List<MarkdownRunCommandTarget> {
    return when (command) {
      is MarkdownRunCommand.Line -> listOf(
        MarkdownRunCommandTarget(
          title = command.command.title,
          icon = AllIcons.RunConfigurations.TestState.Run,
          run = { commandSession.executeLine(command.command, RunnerPlace.PREVIEW) },
        )
      )
      is MarkdownRunCommand.Block -> buildList {
        add(
          MarkdownRunCommandTarget(
            title = MarkdownBundle.message("markdown.runner.launch.block"),
            icon = AllIcons.RunConfigurations.TestState.Run_run,
            run = { commandSession.executeBlock(command) },
          )
        )
        val firstLineCommand = commandSession.lineCommand(command.descriptor.firstLineCommandId)
        if (firstLineCommand != null) {
          add(
            MarkdownRunCommandTarget(
              title = MarkdownBundle.message("markdown.runner.launch.line"),
              icon = AllIcons.RunConfigurations.TestState.Run,
              run = { commandSession.executeLine(firstLineCommand.command, RunnerPlace.PREVIEW) },
            )
          )
        }
      }
    }
  }

  private fun showRunCommandPopup(targets: List<MarkdownRunCommandTarget>, params: MarkdownRunCommandParams) {
    val actionGroup = DefaultActionGroup()
    for (target in targets) {
      actionGroup.add(object : AnAction({ target.title }, target.icon) {
        override fun actionPerformed(e: AnActionEvent) {
          target.run()
        }
      })
    }
    ActionManager.getInstance().createActionPopupMenu(ActionPlaces.EDITOR_GUTTER_POPUP, actionGroup)
      .component.show(rootComponent, params.clientX, params.clientY)
  }

  private data class MarkdownRunCommandTarget(
    val title: String,
    val icon: Icon,
    val run: () -> Unit,
  )

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
            contentVersion = actualUpdate.contentVersion,
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
    val document: VirtualFile?,
    val contentVersion: Int,
  )

  @Service(Service.Level.APP)
  private class ScopeHolder(
    val coroutineScope: CoroutineScope,
  )

  private companion object {
    private val ASSET_ROOT = WebViewAssetRoot
      .forView("markdown-preview")
      .withIconSets(WebViewIconSet.allIcons())

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
