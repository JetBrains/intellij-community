// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.frontend

import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.impl.tabInEditor.ToolWindowEditorTabPresentation
import com.intellij.openapi.wm.impl.tabInEditor.ToolWindowEditorTabSupport
import com.intellij.terminal.frontend.toolwindow.getTerminalTab
import com.intellij.terminal.frontend.toolwindow.impl.TerminalTabCloseListenerImpl
import com.intellij.terminal.frontend.toolwindow.impl.getTitleText
import com.intellij.terminal.frontend.toolwindow.impl.titleStateFlow
import com.intellij.terminal.frontend.view.TerminalView
import com.intellij.terminal.ui.TerminalWidget
import com.intellij.ui.content.Content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.withContext
import org.jetbrains.plugins.terminal.TerminalTabCloseListener
import org.jetbrains.plugins.terminal.TerminalTabCloseListener.CloseCheckResult
import org.jetbrains.plugins.terminal.TerminalToolWindowFactory
import org.jetbrains.plugins.terminal.TerminalToolWindowManager
import org.jetbrains.plugins.terminal.classic.ClassicTerminalTabCloseListener
import org.jetbrains.plugins.terminal.util.TerminalTitleUtils.buildSettingsAwareFullTitle
import org.jetbrains.plugins.terminal.util.TerminalTitleUtils.buildSettingsAwareTitle
import org.jetbrains.plugins.terminal.util.TerminalTitleUtils.stateFlow
import java.beans.PropertyChangeListener
import javax.swing.Icon

private val LOG = logger<TerminalToolWindowEditorTabSupport>()

internal class TerminalToolWindowEditorTabSupport : ToolWindowEditorTabSupport {
  override fun canCloseTab(project: Project, content: Content): Boolean {
    return TerminalTabCloseListener.runCloseQuery(project, content, projectClosing = false) {
      val terminalContent = findTerminalContent(content)
                            ?: return@runCloseQuery CloseCheckResult.CAN_CLOSE_SILENTLY

      TerminalTabCloseListener.runCloseCheckBlocking(project) {
        when (terminalContent) {
          is TerminalContent.Reworked ->
            TerminalTabCloseListenerImpl.shouldConfirmClosing(terminalContent.view)
          is TerminalContent.Classic ->
            ClassicTerminalTabCloseListener.shouldConfirmClosing(terminalContent.widget)
        }
      }
    }
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
        contentIconUpdatesFlow(content),
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
    return when (val terminalContent = findTerminalContent(content)) {
      is TerminalContent.Reworked -> terminalContent.view.getTitleText()
      is TerminalContent.Classic -> terminalContent.widget.terminalTitle.buildSettingsAwareTitle()
      null -> content.displayName
    }
  }

  private fun titleUpdatesFlow(content: Content): Flow<Unit> {
    return when (val terminalContent = findTerminalContent(content)) {
      is TerminalContent.Reworked -> terminalContent.view.titleStateFlow().map { }
      is TerminalContent.Classic -> {
        terminalContent.widget.terminalTitle.stateFlow(
          buildCroppedTitle = { it.buildSettingsAwareTitle() },
          buildFullTitle = { it.buildSettingsAwareFullTitle() },
        ).map { }
      }

      null -> {
        LOG.debug("Terminal title state is unavailable; falling back to Content.displayName")
        contentDisplayNameUpdatesFlow(content)
      }
    }
  }

  private fun contentDisplayNameUpdatesFlow(content: Content): Flow<Unit> {
    return callbackFlow {
      val listener = PropertyChangeListener { event ->
        if (event.propertyName == Content.PROP_DISPLAY_NAME) {
          trySend(Unit)
        }
      }
      content.addPropertyChangeListener(listener)
      awaitClose {
        content.removePropertyChangeListener(listener)
      }
    }
  }

  private fun contentIconUpdatesFlow(content: Content): Flow<Unit> {
    return callbackFlow {
      val listener = PropertyChangeListener { event ->
        if (event.propertyName == Content.PROP_ICON) {
          trySend(Unit)
        }
      }
      content.addPropertyChangeListener(listener)
      awaitClose {
        content.removePropertyChangeListener(listener)
      }
    }
  }
}

private sealed interface TerminalContent {
  data class Reworked(val view: TerminalView) : TerminalContent
  data class Classic(val widget: TerminalWidget) : TerminalContent
}

private fun findTerminalContent(content: Content): TerminalContent? {
  content.getTerminalTab()?.view?.let {
    return TerminalContent.Reworked(it)
  }

  TerminalToolWindowManager.findWidgetByContent(content)?.let {
    return TerminalContent.Classic(it)
  }

  return null
}
