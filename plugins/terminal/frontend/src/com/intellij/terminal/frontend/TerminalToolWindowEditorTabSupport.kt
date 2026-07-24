// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.frontend

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.jetbrains.plugins.terminal.TerminalTabCloseListener
import org.jetbrains.plugins.terminal.TerminalTabCloseListener.CloseCheckResult
import org.jetbrains.plugins.terminal.TerminalToolWindowFactory
import org.jetbrains.plugins.terminal.TerminalToolWindowManager
import org.jetbrains.plugins.terminal.classic.ClassicTerminalTabCloseListener
import org.jetbrains.plugins.terminal.util.TerminalTitleUtils.buildSettingsAwareFullTitle
import org.jetbrains.plugins.terminal.util.TerminalTitleUtils.buildSettingsAwareTitle
import org.jetbrains.plugins.terminal.util.TerminalTitleUtils.stateFlow
import org.jetbrains.plugins.terminal.util.terminalProjectScopeBoundToDisposable
import java.beans.PropertyChangeListener
import javax.swing.Icon

private val LOG = logger<TerminalToolWindowEditorTabSupport>()

private val TAB_PRESENTATION_STATE_KEY =
  Key.create<StateFlow<ToolWindowEditorTabPresentation>>(
    "Terminal.EditorTabPresentationState"
  )

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

  override fun getTabPresentationState(
    project: Project,
    content: Content,
  ): StateFlow<ToolWindowEditorTabPresentation> {
    content.getUserData(TAB_PRESENTATION_STATE_KEY)?.let {
      return it
    }

    return synchronized(content) {
      content.getUserData(TAB_PRESENTATION_STATE_KEY)
      ?: createTabPresentationState(project, content).also {
        content.putUserData(TAB_PRESENTATION_STATE_KEY, it)
      }
    }
  }

  private fun createTabPresentationState(project: Project, content: Content): StateFlow<ToolWindowEditorTabPresentation> {
    val presentationStateScope = terminalProjectScopeBoundToDisposable(
      project,
      content,
      "Terminal editor tab presentation state",
    )

    val toolWindowIcon = ToolWindowManager.getInstance(project)
      .getToolWindow(TerminalToolWindowFactory.TOOL_WINDOW_ID)
      ?.icon

    val titleState = createTerminalTitleState(content = content, scope = presentationStateScope)
                     ?: run {
                       LOG.warn("Terminal title state is unavailable; falling back to Content.displayName")
                       contentDisplayNameState(content, presentationStateScope)
                     }

    return combine(
      titleState,
      contentIconFlow(content, toolWindowIcon),
    ) { title, icon ->
      ToolWindowEditorTabPresentation(
        title = title,
        icon = icon,
      )
    }
      .distinctUntilChanged()
      .stateIn(
        scope = presentationStateScope,
        started = SharingStarted.Eagerly,
        initialValue = ToolWindowEditorTabPresentation(
          title = titleState.value,
          icon = content.icon ?: toolWindowIcon,
        ),
      )
  }

  private fun contentDisplayNameState(
    content: Content,
    coroutineScope: CoroutineScope,
  ): StateFlow<@NlsContexts.TabTitle String> {
    return callbackFlow {
      val listener = PropertyChangeListener { event ->
        if (event.propertyName == Content.PROP_DISPLAY_NAME) {
          trySend(content.displayName)
        }
      }
      content.addPropertyChangeListener(listener)
      trySend(content.displayName)
      awaitClose {
        content.removePropertyChangeListener(listener)
      }
    }.distinctUntilChanged()
      .stateIn(
        scope = coroutineScope,
        started = SharingStarted.Eagerly,
        initialValue = content.displayName,
      )
  }

  private fun contentIconFlow(content: Content, toolWindowIcon: Icon?): Flow<Icon?> {
    return callbackFlow {
      val listener = PropertyChangeListener { event ->
        if (event.propertyName == Content.PROP_ICON) {
          trySend(content.icon ?: toolWindowIcon)
        }
      }
      content.addPropertyChangeListener(listener)
      trySend(content.icon ?: toolWindowIcon)
      awaitClose {
        content.removePropertyChangeListener(listener)
      }
    }.distinctUntilChanged()
  }

  private fun createTerminalTitleState(
    content: Content,
    scope: CoroutineScope,
  ): StateFlow<@NlsContexts.TabTitle String>? {
    return when (val terminalContent = findTerminalContent(content)) {
      is TerminalContent.Reworked -> {
        val view = terminalContent.view
        view.titleStateFlow()
          .map { it.croppedText }
          .distinctUntilChanged()
          .stateIn(
            scope = scope,
            started = SharingStarted.Lazily,
            initialValue = view.getTitleText(),
          )
      }

      is TerminalContent.Classic -> {
        val terminalTitle = terminalContent.widget.terminalTitle
        terminalTitle.stateFlow(
          buildCroppedTitle = { it.buildSettingsAwareTitle() },
          buildFullTitle = { it.buildSettingsAwareFullTitle() },
        )
          .map { it.croppedText }
          .distinctUntilChanged()
          .stateIn(
            scope = scope,
            started = SharingStarted.Lazily,
            initialValue = terminalTitle.buildSettingsAwareTitle(false),
          )
      }

      null -> null
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
