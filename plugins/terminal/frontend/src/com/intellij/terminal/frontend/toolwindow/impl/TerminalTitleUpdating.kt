package com.intellij.terminal.frontend.toolwindow.impl

import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.UI
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.project.projectId
import com.intellij.terminal.frontend.view.TerminalView
import com.intellij.ui.content.Content
import fleet.rpc.client.durable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import org.jetbrains.plugins.terminal.block.reworked.session.rpc.TerminalTabsManagerApi
import org.jetbrains.plugins.terminal.startup.TerminalProcessType
import org.jetbrains.plugins.terminal.util.TerminalTitleUtils.TITLE_UPDATE_DELAY
import org.jetbrains.plugins.terminal.util.TerminalTitleUtils.TitleData
import org.jetbrains.plugins.terminal.util.TerminalTitleUtils.buildSettingsAwareFullTitle
import org.jetbrains.plugins.terminal.util.TerminalTitleUtils.buildSettingsAwareTitle
import org.jetbrains.plugins.terminal.util.TerminalTitleUtils.stateFlow
import org.jetbrains.plugins.terminal.util.getNow
import org.jetbrains.plugins.terminal.view.shellIntegration.TerminalCommandExecutionListener
import org.jetbrains.plugins.terminal.view.shellIntegration.TerminalCommandFinishedEvent
import org.jetbrains.plugins.terminal.view.shellIntegration.TerminalOutputStatus

internal fun TerminalView.getTitleText(): @NlsSafe String {
  return title.buildSettingsAwareTitle(isCommandRunning(view = this))
}

internal fun TerminalView.getFullTitleText(): @NlsSafe String {
  return title.buildSettingsAwareFullTitle(isCommandRunning(view = this))
}

private fun isCommandRunning(view: TerminalView): Boolean {
  val isNonShellProcess = view.startupOptionsDeferred.getNow()?.processType == TerminalProcessType.NON_SHELL
  val isExecutingShellCommand = view.shellIntegrationDeferred.getNow()?.outputStatus?.value == TerminalOutputStatus.ExecutingCommand
  return isNonShellProcess || isExecutingShellCommand
}

@OptIn(FlowPreview::class)
internal fun updateTabNameOnTitleChange(
  terminalView: TerminalView,
  content: Content,
  coroutineScope: CoroutineScope,
) {
  coroutineScope.launch(Dispatchers.UI + ModalityState.any().asContextElement()) {
    terminalView.titleStateFlow()
      .debounce(TITLE_UPDATE_DELAY)
      .collect {
        content.displayName = it.croppedText
      }
  }
}

@OptIn(FlowPreview::class)
internal fun updateFileNameOnTitleChange(
  terminalView: TerminalView,
  file: VirtualFile,
  project: Project,
  coroutineScope: CoroutineScope,
) {
  coroutineScope.launch(Dispatchers.UI + ModalityState.any().asContextElement()) {
    terminalView.titleStateFlow()
      .debounce(TITLE_UPDATE_DELAY)
      .collect {
        file.rename(null, it.croppedText)
        FileEditorManager.getInstance(project).updateFilePresentation(file)
      }
  }
}

@OptIn(FlowPreview::class)
internal fun updateBackendTabNameOnTitleChange(
  terminalView: TerminalView,
  backendTabId: Int,
  project: Project,
  scope: CoroutineScope,
) {
  scope.launch {
    terminalView.titleStateFlow()
      .debounce(TITLE_UPDATE_DELAY)
      .collect {
        // Save either user-defined or default tab name, ignore the application title.
        // Because when the tab is restored, the saved application title won't relate to the new terminal session context.
        val tabName = it.userDefinedName ?: it.defaultName ?: return@collect
        durable {
          TerminalTabsManagerApi.getInstance().renameTerminalTab(
            projectId = project.projectId(),
            tabId = backendTabId,
            newName = tabName,
            isUserDefinedName = it.userDefinedName != null
          )
        }
      }
  }
}

private fun TerminalView.titleStateFlow(): Flow<TitleData> {
  val terminalView = this

  val titleStateFlow: Flow<TitleData> = title.stateFlow(
    buildCroppedTitle = { terminalView.getTitleText() },
    buildFullTitle = { terminalView.getFullTitleText() }
  )

  val titleOnCommandFinishFlow: Flow<TitleData> = channelFlow {
    val shellIntegration = terminalView.shellIntegrationDeferred.await()

    val disposable = Disposer.newDisposable()
    shellIntegration.addCommandExecutionListener(disposable, object : TerminalCommandExecutionListener {
      override fun commandFinished(event: TerminalCommandFinishedEvent) {
        val data = TitleData(
          croppedText = terminalView.getTitleText(),
          fullText = terminalView.getFullTitleText(),
          defaultName = terminalView.title.defaultTitle,
          userDefinedName = terminalView.title.userDefinedTitle,
        )
        trySend(data)
      }
    })

    awaitClose { Disposer.dispose(disposable) }
  }

  return merge(titleStateFlow, titleOnCommandFinishFlow).distinctUntilChanged()
}