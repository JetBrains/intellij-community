package com.intellij.terminal.frontend.toolwindow.impl

import com.intellij.openapi.application.UI
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Condition
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.wm.ToolWindow
import com.intellij.platform.project.projectId
import com.intellij.terminal.TerminalTitle
import com.intellij.terminal.TerminalTitleListener
import com.intellij.ui.content.Content
import com.intellij.util.asDisposable
import com.intellij.util.text.UniqueNameGenerator
import fleet.rpc.client.durable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.plugins.terminal.TerminalOptionsProvider
import org.jetbrains.plugins.terminal.block.reworked.session.rpc.TerminalTabsManagerApi

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
  title.addListener(scope) {
    withContext(Dispatchers.UI) {
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
  title.addListener(scope) {
    withContext(Dispatchers.IO) {
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

private fun TerminalTitle.addListener(coroutineScope: CoroutineScope, onChange: suspend (TitleData) -> Unit) {
  val initialValue = TitleData(buildTitle(), userDefinedTitle != null)
  val flow = MutableStateFlow(initialValue)

  addTitleListener(object : TerminalTitleListener {
    override fun onTitleChanged(terminalTitle: TerminalTitle) {
      flow.value = TitleData(terminalTitle.buildTitle(), terminalTitle.userDefinedTitle != null)
    }
  }, parentDisposable = coroutineScope.asDisposable())

  coroutineScope.launch {
    flow.collect {
      onChange(it)
    }
  }
}

internal data class TitleData(@param:NlsSafe val text: String, val isUserDefined: Boolean)