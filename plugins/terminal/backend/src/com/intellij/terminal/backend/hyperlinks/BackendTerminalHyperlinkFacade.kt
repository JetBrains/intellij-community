package com.intellij.terminal.backend.hyperlinks

import com.intellij.diagnostic.PluginException
import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.execution.filters.CompositeFilter
import com.intellij.execution.filters.Filter
import com.intellij.execution.filters.HyperlinkInfo
import com.intellij.execution.filters.InvisibleHyperlinkFilterProvider
import com.intellij.execution.impl.applyToLineRange
import com.intellij.openapi.application.readAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.progress.ProgressManager.checkCanceled
import com.intellij.openapi.project.Project
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.annotations.NativePath
import com.intellij.platform.eel.path.EelPath
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.plugins.terminal.fus.ReworkedTerminalUsageCollector
import org.jetbrains.plugins.terminal.hyperlinks.TerminalHyperlinkId
import org.jetbrains.plugins.terminal.hyperlinks.TerminalHyperlinkNavigator
import org.jetbrains.plugins.terminal.hyperlinks.TerminalHyperlinksModel
import org.jetbrains.plugins.terminal.hyperlinks.TerminalOutputContentUpdate
import org.jetbrains.plugins.terminal.hyperlinks.filter.CompositeFilterWrapper
import org.jetbrains.plugins.terminal.hyperlinks.filter.TerminalFilterScope
import org.jetbrains.plugins.terminal.hyperlinks.menu.BackendHyperlinkInfo
import org.jetbrains.plugins.terminal.hyperlinks.session.TerminalFilterResultInfoDto
import org.jetbrains.plugins.terminal.hyperlinks.session.TerminalHoverLineRequest
import org.jetbrains.plugins.terminal.hyperlinks.session.TerminalHyperlinkInfoDto
import org.jetbrains.plugins.terminal.hyperlinks.session.TerminalHyperlinksOutputEvent
import org.jetbrains.plugins.terminal.hyperlinks.session.toFilterResultInfo
import org.jetbrains.plugins.terminal.view.TerminalOffset
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration.Companion.milliseconds

/**
 * Thread-safe wrapper around [TerminalHyperlinksModel] and [BackendTerminalHyperlinkHighlighter].
 */
internal class BackendTerminalHyperlinkFacade(
  private val debugName: String,
  private val project: Project,
  eelDescriptor: EelDescriptor,
  homeDirectory: EelPath,
  coroutineScope: CoroutineScope,
) {
  private val mutex = Mutex()
  private val filterContext = TerminalHyperlinkFilterContextImpl(eelDescriptor, homeDirectory)
  private val filterWrapper = CompositeFilterWrapper(project, coroutineScope, filterContext).also {
    it.getFilter() // kickstart computation
  }
  // Shared by the eager and the hover paths, so their ids never clash.
  private val hyperlinkIdCounter = AtomicLong()
  private val highlighter = BackendTerminalHyperlinkHighlighter(filterWrapper, hyperlinkIdCounter, coroutineScope)

  private val trimOffset = AtomicReference(TerminalOffset.of(0))
  private val model = TerminalHyperlinksModel(debugName = debugName)

  private val invisibleHyperlinks = BackendTerminalInvisibleHyperlinkStorage()

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

  suspend fun applyContentUpdate(update: TerminalOutputContentUpdate) {
    mutex.withLock {
      trimOffset.set(update.trimStartOffset)
      highlighter.applyUpdate(update)
    }
  }

  /**
   * [newDirectory] - native path inside the environment of [filterContext]'s [EelDescriptor].
   */
  suspend fun updateWorkingDirectory(newDirectory: @NativePath String?) {
    mutex.withLock {
      filterContext.updateCurrentDirectory(newDirectory)
    }
  }

  suspend fun collectResultsAndMaybeStartNewTask(): List<TerminalHyperlinksOutputEvent> {
    return mutex.withLock {
      highlighter.collectResultsAndMaybeStartNewTask()
    }
  }

  suspend fun updateModelState(event: TerminalHyperlinksOutputEvent.HyperlinksUpdated) {
    mutex.withLock {
      model.removeHyperlinks(
        fromAbsoluteOffset = event.coveredStartOffset,
        toAbsoluteOffset = event.coveredEndOffset,
        trimUntilOffset = trimOffset.get().toAbsolute(),
      )
      model.addHyperlinks(event.hyperlinks.map { it.toFilterResultInfo() })
    }
  }

  /**
   * Runs the [InvisibleHyperlinkFilterProvider] filters over the hovered line
   * and remembers the found hyperlinks so that they can be followed by id.
   * Forgets the hyperlinks of the earlier requests that [TerminalHoverLineRequest.retainedIds] does not list.
   */
  suspend fun findInvisibleHyperlinks(request: TerminalHoverLineRequest): List<TerminalFilterResultInfoDto> {
    val scope = TerminalFilterScope(project, filterContext)
    val filters = InvisibleHyperlinkFilterProvider.EP_NAME.extensionList.flatMap { provider ->
      try {
        provider.getFilters(project, scope)
      }
      catch (e: Exception) {
        rethrowControlFlowException(e)
        PluginException.logPluginError(LOG, "Failed to create invisible hyperlink filters", e, provider.javaClass)
        emptyList()
      }
    }
    val results = applyFilters(filters, request)
    val hyperlinkInfos = results.mapNotNull { result ->
      (result as? TerminalHyperlinkInfoDto)?.hyperlinkInfo?.let { result.id to it }
    }.toMap()
    mutex.withLock {
      invisibleHyperlinks.retainRequests(request.retainedIds)
      invisibleHyperlinks.addRequestResult(request.id, hyperlinkInfos)
    }
    return results
  }

  private suspend fun applyFilters(
    filters: List<Filter>,
    request: TerminalHoverLineRequest,
  ): List<TerminalFilterResultInfoDto> {
    if (filters.isEmpty()) return emptyList()
    val filter = CompositeFilter(project, filters).also { it.setForceUseAllFilters(true) }
    val input = HypertextFromCharSequenceAdapter(request.text)
    return readAction {
      val dtos = mutableListOf<TerminalFilterResultInfoDto>()
      filter.applyToLineRange(input, 0, 0) { applyResult ->
        checkCanceled()
        val items = applyResult.filterResult?.resultItems ?: return@applyToLineRange
        for (item in items) {
          dtos += item.toFilterResultDtos(hyperlinkIdCounter) { offset -> request.startOffset + offset }
        }
      }
      dtos
    }
  }

  suspend fun getHyperlink(hyperlinkId: TerminalHyperlinkId): BackendHyperlinkInfo? {
    return mutex.withLock {
      findHyperlinkInfo(hyperlinkId)?.let { hyperlinkInfo ->
        BackendHyperlinkInfo(hyperlinkInfo, highlighter.fakeMouseEvent)
      }
    }
  }

  suspend fun hyperlinkClicked(hyperlinkId: TerminalHyperlinkId, mouseEvent: EditorMouseEvent?) {
    val hyperlink = mutex.withLock {
      findHyperlinkInfo(hyperlinkId)
    } ?: return
    TerminalHyperlinkNavigator.navigate(project, hyperlink, mouseEvent)
    ReworkedTerminalUsageCollector.logHyperlinkFollowed(hyperlink.javaClass)
  }

  // Must be called under the mutex.
  private fun findHyperlinkInfo(hyperlinkId: TerminalHyperlinkId): HyperlinkInfo? =
    model.getHyperlink(hyperlinkId)?.hyperlinkInfo ?: invisibleHyperlinks.findHyperlink(hyperlinkId)
}

private val LOG = logger<BackendTerminalHyperlinkFacade>()
