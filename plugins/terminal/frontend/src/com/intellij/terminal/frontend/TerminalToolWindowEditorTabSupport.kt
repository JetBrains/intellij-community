// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.frontend

import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.impl.tabInEditor.ToolWindowEditorTabPresentation
import com.intellij.openapi.wm.impl.tabInEditor.ToolWindowEditorTabSupport
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.terminal.frontend.toolwindow.getTerminalTab
import com.intellij.terminal.frontend.toolwindow.impl.TerminalTabCloseListenerImpl
import com.intellij.terminal.frontend.toolwindow.impl.confirmTermination
import com.intellij.terminal.frontend.toolwindow.impl.getFullTitleText
import com.intellij.terminal.frontend.toolwindow.impl.getTitleText
import com.intellij.terminal.frontend.toolwindow.impl.titleStateFlow
import com.intellij.terminal.frontend.view.TerminalView
import com.intellij.terminal.ui.TerminalWidget
import com.intellij.ui.content.Content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.withContext
import org.jetbrains.plugins.terminal.TerminalBundle
import org.jetbrains.plugins.terminal.TerminalToolWindowFactory
import org.jetbrains.plugins.terminal.TerminalToolWindowManager
import org.jetbrains.plugins.terminal.util.TerminalTitleUtils.buildSettingsAwareFullTitle
import org.jetbrains.plugins.terminal.util.TerminalTitleUtils.buildSettingsAwareTitle
import org.jetbrains.plugins.terminal.util.TerminalTitleUtils.stateFlow
import java.beans.PropertyChangeListener
import javax.swing.Icon
import kotlin.coroutines.cancellation.CancellationException

private val LOG = logger<TerminalToolWindowEditorTabSupport>()

internal class TerminalToolWindowEditorTabSupport : ToolWindowEditorTabSupport {
  override fun filterTabsToClose(project: Project, contents: List<Content>): List<Content> {
    val terminalContents = contents.mapNotNull { content ->
      content.asTerminalContent
    }
    if (terminalContents.isEmpty()) {
      return contents
    }

    val contentsToConfirm = try {
      runWithModalProgressBlocking(project, TerminalBundle.message("checking.running.terminal.processes.progress")) {
        collectContentsToConfirm(terminalContents)
      }
    }
    catch (_: CancellationException) {
      ProgressManager.checkCanceled()
      return contents
    }

    if (contentsToConfirm.isEmpty()) {
      return contents
    }

    if (confirmTermination(project, contentsToConfirm.map(ConfirmationDetails::title))) {
      return contents
    }

    val contentsToKeepOpen = contentsToConfirm.mapTo(HashSet(contentsToConfirm.size), ConfirmationDetails::content)
    return contents.filterNot(contentsToKeepOpen::contains)
  }

  override fun getTabPresentationFlow(
    project: Project,
    content: Content,
  ): Flow<ToolWindowEditorTabPresentation> {
    return flow {
      suspend fun buildState(): ToolWindowEditorTabPresentation {
        return withContext(Dispatchers.EDT) {
          buildTabPresentation(project, content)
        }
      }

      emit(buildState())

      merge(
        titleUpdatesFlow(content),
        content.propertyUpdatesFlow(Content.PROP_ICON),
      ).collect {
        emit(buildState())
      }
    }.distinctUntilChanged()
  }

  private fun buildTabPresentation(project: Project, content: Content): ToolWindowEditorTabPresentation {
    return ToolWindowEditorTabPresentation(
      title = getTabTitle(content),
      icon = content.icon ?: getToolWindowIcon(project),
    )
  }

  private fun getToolWindowIcon(project: Project): Icon? {
    return ToolWindowManager.getInstance(project)
      .getToolWindow(TerminalToolWindowFactory.TOOL_WINDOW_ID)
      ?.icon
  }

  private fun getTabTitle(content: Content): @NlsContexts.TabTitle String {
    return content.asTerminalContent?.getTabTitle() ?: content.displayName
  }

  private fun titleUpdatesFlow(content: Content): Flow<Unit> {
    return content.asTerminalContent?.getTitleUpdatesFlow() ?: run {
      LOG.debug("Terminal title state is unavailable; falling back to Content.displayName")
      content.propertyUpdatesFlow(Content.PROP_DISPLAY_NAME)
    }
  }

  private suspend fun collectContentsToConfirm(
    terminalContents: List<TerminalContent>,
  ): List<ConfirmationDetails> = coroutineScope {
    terminalContents.map { terminalContent ->
      async { terminalContent.getConfirmationDetails() }
    }.awaitAll().filterNotNull()
  }
}

private sealed interface TerminalContent {
  val content: Content

  @NlsSafe
  fun getTabTitle(): String
  fun getTitleUpdatesFlow(): Flow<Unit>
  suspend fun getConfirmationDetails(): ConfirmationDetails?

  class Reworked(override val content: Content, val view: TerminalView) : TerminalContent {
    override fun getTabTitle(): String = view.getTitleText()

    override fun getTitleUpdatesFlow(): Flow<Unit> = view.titleStateFlow().map { }

    override suspend fun getConfirmationDetails(): ConfirmationDetails? {
      return if (TerminalTabCloseListenerImpl.shouldConfirmClosing(view)) {
        ConfirmationDetails(content, view.getFullTitleText())
      }
      else {
        null
      }
    }
  }

  class Classic(override val content: Content, val widget: TerminalWidget) : TerminalContent {
    override fun getTabTitle(): String = widget.terminalTitle.buildSettingsAwareTitle()

    override fun getTitleUpdatesFlow(): Flow<Unit> = widget.terminalTitle.stateFlow(
      buildCroppedTitle = { it.buildSettingsAwareTitle() },
      buildFullTitle = { it.buildSettingsAwareFullTitle() },
    ).map { }

    override suspend fun getConfirmationDetails(): ConfirmationDetails? = withContext(Dispatchers.IO) {
      if (widget.isCommandRunning()) {
        ConfirmationDetails(content, widget.terminalTitle.buildFullTitle())
      }
      else {
        null
      }
    }
  }
}

private val Content.asTerminalContent: TerminalContent?
  get() {
    getTerminalTab()?.view?.let {
      return TerminalContent.Reworked(this, it)
    }

    TerminalToolWindowManager.findWidgetByContent(this)?.let {
      return TerminalContent.Classic(this, it)
    }

    return null
  }

private fun Content.propertyUpdatesFlow(targetPropertyName: String): Flow<Unit> = callbackFlow {
  val listener = PropertyChangeListener { event ->
    if (event.propertyName == targetPropertyName) {
      trySend(Unit)
    }
  }
  addPropertyChangeListener(listener)
  awaitClose {
    removePropertyChangeListener(listener)
  }
}

private data class ConfirmationDetails(
  val content: Content,
  val title: String,
)
