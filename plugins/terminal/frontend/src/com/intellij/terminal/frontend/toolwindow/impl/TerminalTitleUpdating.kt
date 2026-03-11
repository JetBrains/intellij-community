package com.intellij.terminal.frontend.toolwindow.impl

import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.UI
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.terminal.TerminalTitle
import com.intellij.terminal.TerminalTitleListener
import com.intellij.terminal.frontend.view.TerminalView
import com.intellij.ui.content.Content
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
import org.jetbrains.plugins.terminal.buildSettingsAwareTitle
import org.jetbrains.plugins.terminal.util.TerminalTitleUtils.TITLE_UPDATE_DELAY
import org.jetbrains.plugins.terminal.util.getNow
import org.jetbrains.plugins.terminal.view.shellIntegration.TerminalCommandExecutionListener
import org.jetbrains.plugins.terminal.view.shellIntegration.TerminalCommandFinishedEvent
import org.jetbrains.plugins.terminal.view.shellIntegration.TerminalOutputStatus

@Suppress("HardCodedStringLiteral")
@OptIn(FlowPreview::class)
internal fun updateTabNameOnTitleChange(
  terminalView: TerminalView,
  content: Content,
  coroutineScope: CoroutineScope,
) {
  coroutineScope.launch(Dispatchers.UI + ModalityState.any().asContextElement()) {
    terminalView.titleChangesFlow()
      .distinctUntilChanged()
      .debounce(TITLE_UPDATE_DELAY)
      .collect {
        content.displayName = it
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
    terminalView.titleChangesFlow()
      .distinctUntilChanged()
      .debounce(TITLE_UPDATE_DELAY)
      .collect {
        file.rename(null, it)
        FileEditorManager.getInstance(project).updateFilePresentation(file)
      }
  }
}

private fun TerminalView.titleChangesFlow(): Flow<String> {
  val terminalView = this

  val titleStateFlow: Flow<String> = channelFlow {
    val disposable = Disposer.newDisposable()
    terminalView.title.addTitleListener(object : TerminalTitleListener {
      override fun onTitleChanged(terminalTitle: TerminalTitle) {
        trySend(calculateTitleText(terminalView))
      }
    }, disposable)

    send(calculateTitleText(terminalView))
    awaitClose { Disposer.dispose(disposable) }
  }

  val titleOnCommandFinishFlow: Flow<String> = channelFlow {
    val shellIntegration = terminalView.shellIntegrationDeferred.await()

    val disposable = Disposer.newDisposable()
    shellIntegration.addCommandExecutionListener(disposable, object : TerminalCommandExecutionListener {
      override fun commandFinished(event: TerminalCommandFinishedEvent) {
        trySend(calculateTitleText(terminalView))
      }
    })

    awaitClose { Disposer.dispose(disposable) }
  }

  return merge(titleStateFlow, titleOnCommandFinishFlow)
}

private fun calculateTitleText(terminalView: TerminalView): String {
  val shellIntegration = terminalView.shellIntegrationDeferred.getNow()
  val title = terminalView.title
  return if (shellIntegration?.outputStatus?.value == TerminalOutputStatus.ExecutingCommand) {
    title.buildTitle(false)
  }
  else {
    title.buildSettingsAwareTitle()
  }
}