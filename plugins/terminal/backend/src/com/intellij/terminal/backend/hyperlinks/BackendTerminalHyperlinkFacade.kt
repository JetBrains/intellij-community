package com.intellij.terminal.backend.hyperlinks

import com.intellij.execution.filters.HyperlinkInfoBase
import com.intellij.execution.filters.navigate
import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.project.Project
import com.intellij.terminal.session.TerminalHyperlinkId
import com.intellij.terminal.session.TerminalHyperlinksChangedEvent
import com.intellij.terminal.session.TerminalHyperlinksHeartbeatEvent
import com.intellij.terminal.session.TerminalHyperlinksModelState
import com.intellij.terminal.session.dto.toFilterResultInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import org.jetbrains.plugins.terminal.block.reworked.TerminalOutputModel
import org.jetbrains.plugins.terminal.block.reworked.hyperlinks.TerminalHyperlinksModel
import org.jetbrains.plugins.terminal.fus.ReworkedTerminalUsageCollector
import org.jetbrains.plugins.terminal.hyperlinks.BackendHyperlinkInfo

@ApiStatus.Internal
class BackendTerminalHyperlinkFacade(
  private val project: Project,
  coroutineScope: CoroutineScope,
  outputModel: TerminalOutputModel,
  isInAlternateBuffer: Boolean,
) {

  private val highlighter = BackendTerminalHyperlinkHighlighter(project, coroutineScope, outputModel, isInAlternateBuffer)
  private val model = TerminalHyperlinksModel(if (isInAlternateBuffer) "Backend AltBuf" else "Backend Output", outputModel)

  val heartbeatFlow: Flow<TerminalHyperlinksHeartbeatEvent> get() = highlighter.heartbeatFlow
  private val pendingUpdateEvents = MutableStateFlow(0)

  fun collectResultsAndMaybeStartNewTask(): TerminalHyperlinksChangedEvent? {
    // The event is immediately passed to updateModelState(),
    // but the tests need to wait until it was actually applied, and they wait concurrently.
    // This flow works as a latch: it's locked before we even retrieve the event,
    // and unlocked only after it's applied (or if it's null).
    pendingUpdateEvents.update { it + 1 }
    val modelUpdateEvent = highlighter.collectResultsAndMaybeStartNewTask()
    if (modelUpdateEvent == null) {
      pendingUpdateEvents.update { it - 1 }
    }
    return modelUpdateEvent
  }

  fun updateModelState(event: TerminalHyperlinksChangedEvent): Boolean {
    val removedFrom = event.removeFromOffset
    if (removedFrom != null) {
      model.removeHyperlinks(removedFrom)
    }
    model.addHyperlinks(event.hyperlinks.map { it.toFilterResultInfo() })
    pendingUpdateEvents.update { it - 1 }
    return true
  }

  suspend fun hyperlinkClicked(hyperlinkId: TerminalHyperlinkId, mouseEvent: EditorMouseEvent?) {
    val hyperlink = model.getHyperlink(hyperlinkId)?.hyperlinkInfo ?: return
    withContext(Dispatchers.EDT) { // navigation might need the WIL
      if (hyperlink is HyperlinkInfoBase && mouseEvent != null) {
        hyperlink.navigate(project, mouseEvent.editor, mouseEvent.logicalPosition)
      }
      else {
        hyperlink.navigate(project)
      }
      ReworkedTerminalUsageCollector.logHyperlinkFollowed(hyperlink.javaClass)
    }
  }

  fun getHyperlink(hyperlinkId: TerminalHyperlinkId): BackendHyperlinkInfo? =
    model.getHyperlink(hyperlinkId)?.hyperlinkInfo?.let {
      hyperlinkInfo -> BackendHyperlinkInfo(hyperlinkInfo, highlighter.fakeMouseEvent)
    }

  fun dumpState(): TerminalHyperlinksModelState = model.dumpState()

  @TestOnly
  suspend fun awaitTaskCompletion() {
    highlighter.awaitTaskCompletion()
    pendingUpdateEvents.first { it == 0 } // Wait until the last event is applied.
  }

}
