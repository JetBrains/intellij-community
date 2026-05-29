package com.intellij.terminal.backend.hyperlinks

import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.block.hyperlinks.TerminalHyperlinkFilterContext
import org.jetbrains.plugins.terminal.block.reworked.hyperlinks.TerminalHyperlinksModel
import org.jetbrains.plugins.terminal.fus.ReworkedTerminalUsageCollector
import org.jetbrains.plugins.terminal.hyperlinks.TerminalHyperlinkNavigator
import org.jetbrains.plugins.terminal.hyperlinks.TerminalHyperlinksOutputEvent
import org.jetbrains.plugins.terminal.hyperlinks.TerminalOutputContentUpdate
import org.jetbrains.plugins.terminal.session.impl.TerminalHyperlinkId
import org.jetbrains.plugins.terminal.session.impl.dto.toFilterResultInfo
import org.jetbrains.plugins.terminal.view.TerminalOffset
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration.Companion.milliseconds

@ApiStatus.Internal
class BackendTerminalHyperlinkFacade(
  private val debugName: String,
  private val project: Project,
  coroutineScope: CoroutineScope,
  filterContext: TerminalHyperlinkFilterContext?,
) {
  private val highlighter = BackendTerminalHyperlinkHighlighter(project, coroutineScope, filterContext)
  private val trimOffset = AtomicReference(TerminalOffset.of(0))
  private val model = TerminalHyperlinksModel(
    debugName = debugName,
    trimOffset = { trimOffset.get() }
  )

  val heartbeatFlow: Flow<Unit> = flow {
    while (true) {
      if (highlighter.mayHaveWorkToDo()) {
        emit(Unit)
      }
      delay(20.milliseconds)
    }
  }

  fun applyContentUpdate(update: TerminalOutputContentUpdate) {
    trimOffset.set(update.trimStartOffset)
    highlighter.applyUpdate(update)
  }

  fun collectResultsAndMaybeStartNewTask(): List<TerminalHyperlinksOutputEvent> {
    return highlighter.collectResultsAndMaybeStartNewTask()
  }

  fun updateModelState(event: TerminalHyperlinksOutputEvent.HyperlinksUpdated): Boolean {
    val removedFrom = event.removeFromOffset
    if (removedFrom != null) {
      model.removeHyperlinks(removedFrom)
    }
    model.addHyperlinks(event.hyperlinks.map { it.toFilterResultInfo() })
    return true
  }

  suspend fun hyperlinkClicked(hyperlinkId: TerminalHyperlinkId, mouseEvent: EditorMouseEvent?) {
    val hyperlink = model.getHyperlink(hyperlinkId)?.hyperlinkInfo ?: return
    TerminalHyperlinkNavigator.navigate(project, hyperlink, mouseEvent)
    ReworkedTerminalUsageCollector.logHyperlinkFollowed(hyperlink.javaClass)
  }
}
