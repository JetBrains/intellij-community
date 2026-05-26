package com.intellij.terminal.backend.hyperlinks

import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.project.Project
import com.intellij.util.asDisposable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import org.jetbrains.plugins.terminal.block.hyperlinks.TerminalHyperlinkFilterContext
import org.jetbrains.plugins.terminal.block.reworked.hyperlinks.TerminalHyperlinksModel
import org.jetbrains.plugins.terminal.fus.ReworkedTerminalUsageCollector
import org.jetbrains.plugins.terminal.hyperlinks.BackendHyperlinkInfo
import org.jetbrains.plugins.terminal.hyperlinks.TerminalHyperlinkNavigator
import org.jetbrains.plugins.terminal.hyperlinks.TerminalOutputContentUpdate
import org.jetbrains.plugins.terminal.hyperlinks.TerminalOutputTrimmingUpdate
import org.jetbrains.plugins.terminal.hyperlinks.TerminalOutputUpdate
import org.jetbrains.plugins.terminal.session.impl.TerminalHyperlinkId
import org.jetbrains.plugins.terminal.session.impl.TerminalHyperlinksChangedEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalHyperlinksHeartbeatEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalHyperlinksModelState
import org.jetbrains.plugins.terminal.session.impl.dto.toFilterResultInfo
import org.jetbrains.plugins.terminal.view.TerminalContentChangeEvent
import org.jetbrains.plugins.terminal.view.TerminalOffset
import org.jetbrains.plugins.terminal.view.TerminalOutputModel
import org.jetbrains.plugins.terminal.view.TerminalOutputModelListener
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration.Companion.milliseconds

@ApiStatus.Internal
class BackendTerminalHyperlinkFacade private constructor(
  private val project: Project,
  coroutineScope: CoroutineScope,
  isInAlternateBuffer: Boolean,
  filterContext: TerminalHyperlinkFilterContext?,
) {
  private val highlighter = BackendTerminalHyperlinkHighlighter(project, coroutineScope, isInAlternateBuffer, filterContext)
  private val trimOffset = AtomicReference(TerminalOffset.of(0))
  private val model = TerminalHyperlinksModel(
    debugName = if (isInAlternateBuffer) "Backend AltBuf" else "Backend Output",
    trimOffset = { trimOffset.get() }
  )

  val heartbeatFlow: Flow<TerminalHyperlinksHeartbeatEvent> = flow {
    while (true) {
      if (highlighter.mayHaveWorkToDo()) {
        emit(TerminalHyperlinksHeartbeatEvent(isInAlternateBuffer))
      }
      delay(20.milliseconds)
    }
  }

  private val pendingUpdateEvents = MutableStateFlow(0)

  fun applyContentUpdate(update: TerminalOutputUpdate) {
    if (update is TerminalOutputTrimmingUpdate) {
      trimOffset.set(update.startOffset)
    }
    highlighter.applyUpdate(update)
  }

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
    TerminalHyperlinkNavigator.navigate(project, hyperlink, mouseEvent)
    ReworkedTerminalUsageCollector.logHyperlinkFollowed(hyperlink.javaClass)
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

  companion object {
    fun install(
      project: Project,
      coroutineScope: CoroutineScope,
      outputModel: TerminalOutputModel,
      isInAlternateBuffer: Boolean,
      filterContext: TerminalHyperlinkFilterContext?,
    ): BackendTerminalHyperlinkFacade {
      val facade = BackendTerminalHyperlinkFacade(project, coroutineScope, isInAlternateBuffer, filterContext)

      outputModel.addListener(coroutineScope.asDisposable(), object : TerminalOutputModelListener {
        override fun afterContentChanged(event: TerminalContentChangeEvent) {
          val model = event.model
          val update: TerminalOutputUpdate = if (event.isTrimming) {
            TerminalOutputTrimmingUpdate(
              firstLine = model.firstLineIndex,
              startOffset = model.startOffset,
              endOffset = model.endOffset,
              modificationStamp = model.modificationStamp,
            )
          }
          else {
            val startLine = model.getLineByOffset(event.offset)
            val startOffset = model.getStartOfLine(startLine)
            val endOffset = model.endOffset
            TerminalOutputContentUpdate(
              charsSequence = model.getText(startOffset, endOffset),
              startLine = startLine,
              endLine = model.lastLineIndex,
              startOffset = startOffset,
              modificationStamp = model.modificationStamp,
            )
          }
          facade.applyContentUpdate(update)
        }
      })

      return facade
    }
  }
}
