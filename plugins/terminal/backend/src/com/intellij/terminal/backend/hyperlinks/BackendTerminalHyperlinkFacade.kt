package com.intellij.terminal.backend.hyperlinks

import com.intellij.execution.filters.HyperlinkInfoBase
import com.intellij.execution.filters.navigate
import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.project.Project
import com.intellij.terminal.session.TerminalHyperlinkId
import com.intellij.terminal.session.TerminalHyperlinksChangedEvent
import com.intellij.terminal.session.TerminalHyperlinksModelState
import com.intellij.terminal.session.dto.toFilterResultInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import org.jetbrains.plugins.terminal.block.reworked.TerminalOutputModel
import org.jetbrains.plugins.terminal.block.reworked.hyperlinks.TerminalHyperlinksModel
import org.jetbrains.plugins.terminal.fus.ReworkedTerminalUsageCollector

@ApiStatus.Internal
class BackendTerminalHyperlinkFacade(
  private val project: Project,
  coroutineScope: CoroutineScope,
  private val outputModel: TerminalOutputModel,
  isInAlternateBuffer: Boolean,
) {

  private val highlighter = BackendTerminalHyperlinkHighlighter(project, coroutineScope, outputModel, isInAlternateBuffer)
  private val model = TerminalHyperlinksModel(if (isInAlternateBuffer) "Backend AltBuf" else "Backend Output", outputModel)

  val resultFlow: Flow<TerminalHyperlinksChangedEvent> get() = highlighter.resultFlow

  fun updateModelState(event: TerminalHyperlinksChangedEvent): Boolean {
    if (event.documentModificationStamp < outputModel.document.modificationStamp) return false
    val removedFrom = event.removeFromOffset
    if (removedFrom != null) {
      model.removeHyperlinks(removedFrom)
    }
    model.addHyperlinks(event.hyperlinks.map { it.toFilterResultInfo() })
    // If we have applied all hyperlinks corresponding to the current modification stamp,
    // we should mark the task as complete so that the new tasks will only affect newly modified regions.
    if (event.isLastEventInTheBatch) {
      highlighter.finishCurrentTask()
    }
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

  fun dumpState(): TerminalHyperlinksModelState = model.dumpState()

  @TestOnly
  suspend fun awaitTaskCompletion() {
    highlighter.awaitTaskCompletion()
  }

}
