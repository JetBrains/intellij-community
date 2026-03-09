package com.intellij.terminal.frontend.toolwindow.impl

import com.intellij.openapi.application.UI
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Condition
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.wm.ToolWindow
import com.intellij.platform.project.projectId
import com.intellij.terminal.TerminalTitle
import com.intellij.terminal.TerminalTitleListener
import com.intellij.ui.content.Content
import com.intellij.util.text.UniqueNameGenerator
import fleet.rpc.client.durable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.jetbrains.plugins.terminal.TerminalOptionsProvider
import org.jetbrains.plugins.terminal.block.reworked.session.rpc.TerminalTabsManagerApi
import org.jetbrains.plugins.terminal.buildSettingsAwareTitle

internal fun createDefaultTabName(toolWindow: ToolWindow): String {
  val existingNames = toolWindow.contentManager.contentsRecursively.map { it.displayName }
  val defaultName = TerminalOptionsProvider.instance.tabName
  return UniqueNameGenerator.generateUniqueName(
    defaultName,
    "",
    "",
    " (",
    ")",
    Condition { !existingNames.contains(it) }
  )
}

internal fun updateTabNameOnTitleChange(title: TerminalTitle, content: Content, scope: CoroutineScope) {
  scope.launch(Dispatchers.UI) {
    title.stateFlow().collect {
      content.displayName = it.text
    }
  }
}

internal fun updateBackendTabNameOnTitleChange(
  title: TerminalTitle,
  backendTabId: Int,
  project: Project,
  scope: CoroutineScope,
) {
  scope.launch {
    title.stateFlow().collect {
      durable {
        TerminalTabsManagerApi.getInstance().renameTerminalTab(
          project.projectId(),
          backendTabId,
          it.text,
          it.isUserDefined
        )
      }
    }
  }
}

private fun TerminalTitle.stateFlow(): Flow<TitleData> {
  val flow = channelFlow {
    val disposable = Disposer.newDisposable()
    addTitleListener(object : TerminalTitleListener {
      override fun onTitleChanged(terminalTitle: TerminalTitle) {
        trySend(TitleData(terminalTitle.buildSettingsAwareTitle(), terminalTitle.userDefinedTitle != null))
      }
    }, disposable)

    send(TitleData(buildSettingsAwareTitle(), userDefinedTitle != null))

    awaitClose { Disposer.dispose(disposable) }
  }

  return flow.distinctUntilChanged()
}

internal data class TitleData(@param:NlsSafe val text: String, val isUserDefined: Boolean)