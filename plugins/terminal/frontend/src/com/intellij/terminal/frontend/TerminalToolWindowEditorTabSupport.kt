// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.frontend

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.impl.tabInEditor.ToolWindowEditorTabDescriptor
import com.intellij.openapi.wm.impl.tabInEditor.ToolWindowEditorTabSupport
import com.intellij.ui.content.Content
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import org.jetbrains.plugins.terminal.TerminalEditorTabSupportUtil
import org.jetbrains.plugins.terminal.TerminalTabCloseListener
import org.jetbrains.plugins.terminal.TerminalTabCloseListener.CloseCheckResult
import org.jetbrains.plugins.terminal.TerminalToolWindowFactory
import org.jetbrains.plugins.terminal.util.terminalProjectScopeBoundToDisposable
import java.beans.PropertyChangeListener
import javax.swing.Icon

private val LOG = logger<TerminalToolWindowEditorTabSupport>()

private val TAB_DESCRIPTOR_STATE_KEY =
  Key.create<StateFlow<ToolWindowEditorTabDescriptor>>(
    "Terminal.EditorTabDescriptorState"
  )

internal class TerminalToolWindowEditorTabSupport : ToolWindowEditorTabSupport {
  override fun canCloseFile(project: Project, content: Content): Boolean {
    return TerminalTabCloseListener.runCloseQuery(project, content, projectClosing = false) {
      val info = content.getUserData(TerminalEditorTabSupportUtil.TERMINAL_EDITOR_TAB_INFO_KEY)
                 ?: return@runCloseQuery CloseCheckResult.CAN_CLOSE_SILENTLY
      TerminalTabCloseListener.runCloseCheckBlocking(project) {
        info.shouldConfirmClosing()
      }
    }
  }

  override fun getTabDescriptorState(
    project: Project,
    content: Content,
  ): StateFlow<ToolWindowEditorTabDescriptor> {
    content.getUserData(TAB_DESCRIPTOR_STATE_KEY)?.let {
      return it
    }

    return synchronized(content) {
      content.getUserData(TAB_DESCRIPTOR_STATE_KEY)
      ?: createTabDescriptorState(project, content).also {
        content.putUserData(TAB_DESCRIPTOR_STATE_KEY, it)
      }
    }
  }

  private fun createTabDescriptorState(project: Project, content: Content): StateFlow<ToolWindowEditorTabDescriptor> {
    val descriptorStateScope = terminalProjectScopeBoundToDisposable(
      project,
      content,
      "Terminal editor tab descriptor state",
    )

    val toolWindowIcon = ToolWindowManager.getInstance(project)
      .getToolWindow(TerminalToolWindowFactory.TOOL_WINDOW_ID)
      ?.icon

    // The info must be set before the descriptor state is created.
    // We do not observe changes to this user-data key.
    val info = content.getUserData(TerminalEditorTabSupportUtil.TERMINAL_EDITOR_TAB_INFO_KEY)

    val titleState: StateFlow<@NlsContexts.TabTitle String> =
      info?.editorTabTitle ?: run {
        LOG.warn(
          "Terminal editor tab info is not defined; falling back to Content.displayName"
        )
        contentDisplayNameState(content, descriptorStateScope)
      }

    return combine(
      titleState,
      contentIconFlow(content, toolWindowIcon),
    ) { title, icon ->
      ToolWindowEditorTabDescriptor(
        title = title,
        icon = icon,
      )
    }
      .distinctUntilChanged()
      .stateIn(
        scope = descriptorStateScope,
        started = SharingStarted.Eagerly,
        initialValue = ToolWindowEditorTabDescriptor(
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
}
