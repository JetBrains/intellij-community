package com.intellij.terminal.backend.hyperlinks

import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.project.Project
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.annotations.NativePath
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import org.jetbrains.plugins.terminal.fus.ReworkedTerminalUsageCollector
import org.jetbrains.plugins.terminal.hyperlinks.TerminalHyperlinkId
import org.jetbrains.plugins.terminal.hyperlinks.TerminalHyperlinkNavigator
import org.jetbrains.plugins.terminal.hyperlinks.TerminalHyperlinksModel
import org.jetbrains.plugins.terminal.hyperlinks.TerminalOutputContentUpdate
import org.jetbrains.plugins.terminal.hyperlinks.filter.CompositeFilterWrapper
import org.jetbrains.plugins.terminal.hyperlinks.menu.BackendHyperlinkInfo
import org.jetbrains.plugins.terminal.hyperlinks.session.TerminalHyperlinksOutputEvent
import org.jetbrains.plugins.terminal.hyperlinks.session.toFilterResultInfo
import org.jetbrains.plugins.terminal.view.TerminalOffset
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration.Companion.milliseconds

internal class BackendTerminalHyperlinkFacade(
  private val debugName: String,
  private val project: Project,
  eelDescriptor: EelDescriptor,
  coroutineScope: CoroutineScope,
) {
  private val filterContext = TerminalHyperlinkFilterContextImpl(eelDescriptor)
  private val filterWrapper = CompositeFilterWrapper(project, coroutineScope, filterContext).also {
    it.getFilter() // kickstart computation
  }
  private val highlighter = BackendTerminalHyperlinkHighlighter(filterWrapper, coroutineScope)

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

  /** Fired when [com.intellij.execution.filters.Filter]'s list is changed in [filterWrapper] */
  val filterUpdatesFlow: Flow<Unit>
    get() = filterWrapper.getFilterFlow().map { /*Unit*/ }

  fun applyContentUpdate(update: TerminalOutputContentUpdate) {
    trimOffset.set(update.trimStartOffset)
    highlighter.applyUpdate(update)
  }

  /**
   * [newDirectory] - native path inside the environment of [filterContext]'s [EelDescriptor].
   */
  fun updateWorkingDirectory(newDirectory: @NativePath String?) {
    filterContext.updateCurrentDirectory(newDirectory)
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

  fun getHyperlink(hyperlinkId: TerminalHyperlinkId): BackendHyperlinkInfo? {
    return model.getHyperlink(hyperlinkId)?.hyperlinkInfo?.let { hyperlinkInfo ->
      BackendHyperlinkInfo(hyperlinkInfo, highlighter.fakeMouseEvent)
    }
  }

  suspend fun hyperlinkClicked(hyperlinkId: TerminalHyperlinkId, mouseEvent: EditorMouseEvent?) {
    val hyperlink = model.getHyperlink(hyperlinkId)?.hyperlinkInfo ?: return
    TerminalHyperlinkNavigator.navigate(project, hyperlink, mouseEvent)
    ReworkedTerminalUsageCollector.logHyperlinkFollowed(hyperlink.javaClass)
  }
}
