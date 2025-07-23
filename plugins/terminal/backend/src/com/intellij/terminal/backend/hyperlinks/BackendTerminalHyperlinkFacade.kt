package com.intellij.terminal.backend.hyperlinks

import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.terminal.session.TerminalHyperlinkId
import com.intellij.terminal.session.TerminalHyperlinksChangedEvent
import com.intellij.terminal.session.TerminalHyperlinksModelState
import com.intellij.terminal.session.dto.toFilterResultInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.jetbrains.plugins.terminal.block.reworked.TerminalOutputModelImpl
import org.jetbrains.plugins.terminal.block.reworked.hyperlinks.TerminalHyperlinksModel
import org.jetbrains.plugins.terminal.fus.ReworkedTerminalUsageCollector

internal class BackendTerminalHyperlinkFacade(
  private val project: Project,
  coroutineScope: CoroutineScope,
  private val outputModel: TerminalOutputModelImpl,
  isInAlternateBuffer: Boolean,
) {

  private val highlighter = BackendTerminalHyperlinkHighlighter(project, coroutineScope, outputModel, isInAlternateBuffer)
  private val model = TerminalHyperlinksModel(if (isInAlternateBuffer) "Backend AltBuf" else "Backend Output", outputModel)

  val resultFlow: Flow<List<TerminalHyperlinksChangedEvent>> get() = highlighter.resultFlow

  fun updateModelState(event: TerminalHyperlinksChangedEvent) {
    if (event.documentModificationStamp == outputModel.document.modificationStamp) {
      val removedFrom = event.absoluteStartOffset
      if (removedFrom != null) {
        model.removeHyperlinks(removedFrom)
      }
      model.addHyperlinks(event.hyperlinks.map { it.toFilterResultInfo() })
      // If we have applied all hyperlinks corresponding to the current modification stamp,
      // we should mark the task as complete so that the new tasks will only affect newly modified regions.
      if (event.isLastEventInTheBatch) {
        highlighter.finishCurrentTask()
      }
    }
  }

  suspend fun hyperlinkClicked(hyperlinkId: TerminalHyperlinkId) {
    val hyperlink = model.getHyperlink(hyperlinkId)?.hyperlinkInfo ?: return
    withContext(Dispatchers.EDT) { // navigation might need the WIL
      hyperlink.navigate(project)
      ReworkedTerminalUsageCollector.logHyperlinkFollowed(hyperlink.javaClass)
    }
  }

  fun dumpState(): TerminalHyperlinksModelState = model.dumpState()

}
