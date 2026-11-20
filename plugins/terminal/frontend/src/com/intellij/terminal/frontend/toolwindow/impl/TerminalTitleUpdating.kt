package com.intellij.terminal.frontend.toolwindow.impl

import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.UI
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.util.coroutines.flow.throttleLatest
import com.intellij.terminal.frontend.view.TerminalView
import com.intellij.ui.content.Content
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import org.jetbrains.plugins.terminal.startup.TerminalProcessType
import org.jetbrains.plugins.terminal.util.TerminalTitleUtils.TITLE_UPDATE_DELAY
import org.jetbrains.plugins.terminal.util.TerminalTitleUtils.TitleData
import org.jetbrains.plugins.terminal.util.TerminalTitleUtils.buildSettingsAwareFullTitle
import org.jetbrains.plugins.terminal.util.TerminalTitleUtils.buildSettingsAwareTitle
import org.jetbrains.plugins.terminal.util.TerminalTitleUtils.stateFlow
import org.jetbrains.plugins.terminal.util.getNow
import org.jetbrains.plugins.terminal.view.shellIntegration.TerminalOutputStatus

internal fun TerminalView.getTitleText(): @NlsSafe String {
  return title.buildSettingsAwareTitle(isCommandRunning(view = this))
}

internal fun TerminalView.getFullTitleText(): @NlsSafe String {
  return title.buildSettingsAwareFullTitle(isCommandRunning(view = this))
}

private fun isCommandRunning(view: TerminalView): Boolean {
  val isNonShellProcess = view.startupOptionsDeferred.getNow()?.processType == TerminalProcessType.NON_SHELL
  val isExecutingShellCommand = view.shellLifecycle.outputStatus.value == TerminalOutputStatus.ExecutingCommand
  return isNonShellProcess || isExecutingShellCommand
}

internal fun updateTabNameOnTitleChange(
  terminalView: TerminalView,
  content: Content,
  coroutineScope: CoroutineScope,
) {
  coroutineScope.launch(Dispatchers.UI + ModalityState.any().asContextElement()) {
    terminalView.titleStateFlow()
      .throttleLatest(TITLE_UPDATE_DELAY)
      .collect {
        content.displayName = it.croppedText
        content.description = StringUtil.escapeXmlEntities(it.fullText)
      }
  }
}

internal fun updateFileNameOnTitleChange(
  terminalView: TerminalView,
  file: VirtualFile,
  project: Project,
  coroutineScope: CoroutineScope,
) {
  coroutineScope.launch(Dispatchers.UI + ModalityState.any().asContextElement()) {
    terminalView.titleStateFlow()
      .throttleLatest(TITLE_UPDATE_DELAY)
      .collect {
        file.rename(null, it.croppedText)
        FileEditorManager.getInstance(project).updateFilePresentation(file)
      }
  }
}

internal fun TerminalView.titleStateFlow(): Flow<TitleData> {
  val terminalView = this

  val titleStateFlow: Flow<TitleData> = title.stateFlow(
    buildCroppedTitle = { terminalView.getTitleText() },
    buildFullTitle = { terminalView.getFullTitleText() }
  )

  val shellStateTitleFlow = shellLifecycle.outputStatus.map {
    TitleData(
      croppedText = terminalView.getTitleText(),
      fullText = terminalView.getFullTitleText(),
      defaultName = terminalView.title.defaultTitle,
      userDefinedName = terminalView.title.userDefinedTitle,
    )
  }

  return merge(titleStateFlow, shellStateTitleFlow).distinctUntilChanged()
}