// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.frontend

import com.intellij.openapi.application.EDT
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.impl.tabInEditor.ToolWindowEditorTabPresentation
import com.intellij.openapi.wm.impl.tabInEditor.ToolWindowEditorTabSupport
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.platform.util.coroutines.flow.throttleLatest
import com.intellij.terminal.frontend.toolwindow.impl.TerminalTabContent
import com.intellij.terminal.frontend.toolwindow.impl.TerminalTabContent.ClosingConfirmationDetails
import com.intellij.terminal.frontend.toolwindow.impl.confirmTermination
import com.intellij.terminal.frontend.toolwindow.impl.isTerminalTabContent
import com.intellij.terminal.frontend.toolwindow.impl.toTerminalTabContent
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
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.withContext
import org.jetbrains.plugins.terminal.TerminalBundle
import org.jetbrains.plugins.terminal.TerminalToolWindowFactory
import org.jetbrains.plugins.terminal.util.TerminalTitleUtils.TITLE_UPDATE_DELAY
import java.beans.PropertyChangeListener
import javax.swing.Icon
import kotlin.coroutines.cancellation.CancellationException

internal class TerminalToolWindowEditorTabSupport : ToolWindowEditorTabSupport {
  override fun filterTabsToClose(project: Project, contents: List<Content>): List<Content> {
    if (contents.isEmpty()) return contents
    val terminalContents = contents.map { content ->
      content.toTerminalTabContent()
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

    if (confirmTermination(project, contentsToConfirm.map(ClosingConfirmationDetails::fullTitle))) {
      return contents
    }

    val contentsToKeepOpen = contentsToConfirm.mapTo(HashSet(contentsToConfirm.size), ClosingConfirmationDetails::content)
    return contents.filterNot(contentsToKeepOpen::contains)
  }

  override fun getTabPresentationFlow(
    project: Project,
    content: Content,
  ): Flow<ToolWindowEditorTabPresentation> {
    return flow {
      suspend fun buildPresentation(): ToolWindowEditorTabPresentation {
        return withContext(Dispatchers.EDT) {
          buildTabPresentation(project, content)
        }
      }

      emit(buildPresentation())

      merge(
        titleUpdatesFlow(content)
          .throttleLatest(TITLE_UPDATE_DELAY),
        content.propertyUpdatesFlow(Content.PROP_ICON),
      ).collect {
        emit(buildPresentation())
      }
    }.distinctUntilChanged()
  }

  override fun canBeMovedToEditor(content: Content): Boolean {
    return content.isTerminalTabContent()
  }

  private fun buildTabPresentation(project: Project, content: Content): ToolWindowEditorTabPresentation {
    val terminalContent = content.toTerminalTabContent()
    return ToolWindowEditorTabPresentation(
      title = terminalContent.getTabTitle(),
      icon = content.icon ?: getToolWindowIcon(project),
      tooltip = HtmlChunk.text(terminalContent.getFullTabTitle()),
    )
  }

  private fun getToolWindowIcon(project: Project): Icon? {
    return ToolWindowManager.getInstance(project)
      .getToolWindow(TerminalToolWindowFactory.TOOL_WINDOW_ID)
      ?.icon
  }

  private fun titleUpdatesFlow(content: Content): Flow<Unit> {
    return content.toTerminalTabContent().titleUpdatesFlow()
  }

  private suspend fun collectContentsToConfirm(
    terminalContents: List<TerminalTabContent>,
  ): List<ClosingConfirmationDetails> = coroutineScope {
    terminalContents.map { terminalContent ->
      async { terminalContent.getClosingConfirmationDetails() }
    }.awaitAll().filterNotNull()
  }
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