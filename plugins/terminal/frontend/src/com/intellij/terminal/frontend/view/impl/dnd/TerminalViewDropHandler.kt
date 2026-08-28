package com.intellij.terminal.frontend.view.impl.dnd

import com.intellij.ide.dnd.DnDDropHandler
import com.intellij.ide.dnd.DnDEvent
import com.intellij.ide.dnd.DnDNativeTarget
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.UI
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.terminal.frontend.dnd.TerminalDropData
import com.intellij.terminal.frontend.dnd.TerminalDroppedContentResolver
import com.intellij.terminal.frontend.toolwindow.impl.getRunningProcessCommandLine
import com.intellij.terminal.frontend.toolwindow.impl.getTerminalContext
import com.intellij.terminal.frontend.view.TerminalView
import com.intellij.terminal.frontend.view.impl.TerminalOutputScrollingModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.plugins.terminal.fus.ReworkedTerminalUsageCollector
import org.jetbrains.plugins.terminal.fus.TerminalCommandUsageStatistics
import org.jetbrains.plugins.terminal.fus.TerminalInsertedContentSource

/**
 * Handles drag-and-drop directly on a [TerminalView].
 *
 * Dropped files and directories are converted to terminal-compatible paths and
 * inserted into the active terminal using bracketed paste mode.
 * Plain-text drops are inserted as is.
 *
 * Supports drops from Project View (PSI elements), native OS file managers, and plain-text drag sources.
 */
internal class TerminalViewDropHandler(
  private val project: Project,
  private val terminalView: TerminalView,
  private val scrollingModel: TerminalOutputScrollingModel,
) : DnDDropHandler {
  override fun drop(event: DnDEvent) {
    val data = TerminalDropData(event)
    val context = getTerminalContext(terminalView) ?: return
    val modalityState = ModalityState.current()
    val fileSource = if (event.attachedObject is DnDNativeTarget.EventInfo) {
      TerminalInsertedContentSource.EXTERNAL_APP
    }
    else TerminalInsertedContentSource.IDE

    terminalView.coroutineScope.launch {
      val text = TerminalDroppedContentResolver.resolveText(
        data = data,
        terminalContext = context,
        projectEelDescriptor = project.getEelDescriptor(),
      )

      if (text.isNullOrBlank()) {
        return@launch
      }

      terminalView.createSendTextBuilder()
        .useBracketedPasteMode()
        .send(text)

      val commandLine = terminalView.getRunningProcessCommandLine()
      val processExecutable = commandLine?.let {
        TerminalCommandUsageStatistics.getLoggableCommandData(commandLine, expandAbsoluteOrRelativePath = true).command
      }
      ReworkedTerminalUsageCollector.logContentInserted(
        project = project,
        contentType = data.getContentType(),
        fileSource = fileSource,
        processExecutable = processExecutable,
      )

      withContext(Dispatchers.UI + modalityState.asContextElement()) {
        IdeFocusManager.getInstance(project).requestFocusInProject(terminalView.preferredFocusableComponent, project)
        scrollingModel.scrollToCursor(true)
      }
    }
  }
}
