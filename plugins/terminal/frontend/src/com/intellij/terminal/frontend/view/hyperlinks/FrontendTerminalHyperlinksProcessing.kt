package com.intellij.terminal.frontend.view.hyperlinks

import com.intellij.execution.impl.EditorTextDecoration
import com.intellij.execution.impl.EditorTextDecorationApplier
import com.intellij.execution.impl.buildHighlighting
import com.intellij.execution.impl.buildHyperlink
import com.intellij.execution.impl.buildInlay
import com.intellij.execution.impl.createEditorTextDecorationApplier
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.UI
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.project.Project
import com.intellij.platform.project.projectId
import com.intellij.platform.util.coroutines.childScope
import com.intellij.terminal.frontend.view.impl.toRelative
import com.intellij.util.AwaitCancellationAndInvoke
import com.intellij.util.asDisposable
import com.intellij.util.awaitCancellationAndInvoke
import com.intellij.util.concurrency.annotations.RequiresEdt
import fleet.rpc.client.durable
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.plugins.terminal.block.reworked.hyperlinks.TerminalHyperlinksModel
import org.jetbrains.plugins.terminal.hyperlinks.TerminalHyperlinkClickedEvent
import org.jetbrains.plugins.terminal.hyperlinks.TerminalHyperlinksOutputEvent
import org.jetbrains.plugins.terminal.hyperlinks.TerminalHyperlinksSession
import org.jetbrains.plugins.terminal.hyperlinks.TerminalOutputContentUpdate
import org.jetbrains.plugins.terminal.hyperlinks.TerminalOutputTrimmingUpdate
import org.jetbrains.plugins.terminal.hyperlinks.TerminalOutputUpdate
import org.jetbrains.plugins.terminal.hyperlinks.rpc.TerminalHyperlinksInputEvent
import org.jetbrains.plugins.terminal.hyperlinks.rpc.TerminalHyperlinksRemoteApi
import org.jetbrains.plugins.terminal.hyperlinks.rpc.TerminalHyperlinksSessionRemoteApi
import org.jetbrains.plugins.terminal.hyperlinks.rpc.toDto
import org.jetbrains.plugins.terminal.session.impl.TerminalFilterResultInfo
import org.jetbrains.plugins.terminal.session.impl.TerminalHighlightingInfo
import org.jetbrains.plugins.terminal.session.impl.TerminalHyperlinkId
import org.jetbrains.plugins.terminal.session.impl.TerminalHyperlinkInfo
import org.jetbrains.plugins.terminal.session.impl.TerminalInlayInfo
import org.jetbrains.plugins.terminal.session.impl.dto.toFilterResultInfo
import org.jetbrains.plugins.terminal.session.impl.toPlatformId
import org.jetbrains.plugins.terminal.view.TerminalContentChangeEvent
import org.jetbrains.plugins.terminal.view.TerminalLineIndex
import org.jetbrains.plugins.terminal.view.TerminalOffset
import org.jetbrains.plugins.terminal.view.TerminalOutputModel
import org.jetbrains.plugins.terminal.view.TerminalOutputModelListener
import kotlin.time.Duration.Companion.milliseconds

internal fun installHyperlinksProcessing(
  project: Project,
  outputModel: TerminalOutputModel,
  editor: EditorEx,
  coroutineScope: CoroutineScope,
): FrontendTerminalHyperlinkFacade {
  val applier = createEditorTextDecorationApplier(editor, coroutineScope.asDisposable())
  coroutineScope.launch {
    processHyperlinks(project, outputModel, applier)
  }
  return FrontendTerminalHyperlinkFacade(applier)
}

private suspend fun processHyperlinks(
  project: Project,
  outputModel: TerminalOutputModel,
  applier: EditorTextDecorationApplier,
) = coroutineScope {
  val scope = this
  val session = createHyperlinksSession(project, scope.childScope("FrontendTerminalHyperlinksSession"))

  launch(CoroutineName("Output model tracking")) {
    trackOutputModelChanges(outputModel, session.inputEventsSink)
  }
  // Can't use Dispatchers.UI because editor can require locks
  launch(Dispatchers.EDT + ModalityState.any().asContextElement() + CoroutineName("Results processing")) {
    processHyperlinkResults(
      debugName = "Frontend#${session.id.id}",
      outputModel = outputModel,
      applier = applier,
      hyperlinkUpdatesChannel = session.hyperlinkUpdatesChannel,
      onLinkClicked = { id, mouseEvent ->
        scope.launch {
          session.handleHyperlinkClick(TerminalHyperlinkClickedEvent(id, mouseEvent))
        }
      }
    )
  }
}

@OptIn(AwaitCancellationAndInvoke::class)
private suspend fun createHyperlinksSession(project: Project, coroutineScope: CoroutineScope): TerminalHyperlinksSession {
  val sessionId = durable {
    TerminalHyperlinksRemoteApi.getInstance().createNewSession(project.projectId())
  }
  coroutineScope.awaitCancellationAndInvoke(Dispatchers.Default) {
    TerminalHyperlinksRemoteApi.getInstance().closeSession(sessionId)
  }

  val sessionApi = TerminalHyperlinksSessionRemoteApi.getInstance()
  val inputEventsSink = durable {
    sessionApi.getInputEventsSink(sessionId)
  }
  val hyperlinkUpdatesChannel = durable {
    sessionApi.getHyperlinkUpdatesChannel(sessionId)
  }
  return FrontendTerminalHyperlinksSession(
    id = sessionId,
    inputEventsSink = inputEventsSink,
    hyperlinkUpdatesChannel = hyperlinkUpdatesChannel
  )
}

private suspend fun trackOutputModelChanges(
  outputModel: TerminalOutputModel,
  sink: SendChannel<TerminalHyperlinksInputEvent>,
) = coroutineScope {
  val tracker = TerminalOutputModelChangesTracker(outputModel, parentDisposable = this.asDisposable())

  // Send content updates to the backend periodically.
  while (true) {
    val updates = withContext(Dispatchers.UI + ModalityState.any().asContextElement()) {
      try {
        getContentUpdates(outputModel, tracker)
      }
      catch (e: Exception) {
        LOG.error("Error when collecting content updates", e)
        emptyList()
      }
    }

    for (update in updates) {
      sink.send(TerminalHyperlinksInputEvent.ContentUpdated(update.toDto()))
    }

    delay(20.milliseconds)
  }
}

@RequiresEdt
private fun getContentUpdates(
  outputModel: TerminalOutputModel,
  tracker: TerminalOutputModelChangesTracker,
): List<TerminalOutputUpdate> {
  val firstChangedLine = tracker.getFirstChangedLineAndReset()
  return if (firstChangedLine != null) {
    val trimmingUpdate = TerminalOutputTrimmingUpdate(
      firstLine = outputModel.firstLineIndex,
      startOffset = outputModel.startOffset,
      endOffset = outputModel.endOffset,
      modificationStamp = outputModel.modificationStamp,
    )

    val startOffset = outputModel.getStartOfLine(firstChangedLine)
    val contentUpdate = TerminalOutputContentUpdate(
      charsSequence = outputModel.getText(startOffset, outputModel.endOffset),
      startLine = firstChangedLine,
      endLine = outputModel.lastLineIndex,
      startOffset = startOffset,
      modificationStamp = outputModel.modificationStamp,
    )
    listOf(trimmingUpdate, contentUpdate)
  }
  else emptyList()
}

private suspend fun processHyperlinkResults(
  debugName: String,
  outputModel: TerminalOutputModel,
  applier: EditorTextDecorationApplier,
  hyperlinkUpdatesChannel: ReceiveChannel<TerminalHyperlinksOutputEvent>,
  onLinkClicked: (TerminalHyperlinkId, EditorMouseEvent) -> Unit,
) {
  val hyperlinksModel = TerminalHyperlinksModel(
    debugName = debugName,
    trimOffset = { outputModel.startOffset },
  )

  for (event in hyperlinkUpdatesChannel) {
    try {
      processHyperlinksOutputEvent(outputModel, hyperlinksModel, applier, event, onLinkClicked)
    }
    catch (e: Exception) {
      LOG.error("Error when processing hyperlinks update: $event", e)
    }
  }
}

private fun processHyperlinksOutputEvent(
  outputModel: TerminalOutputModel,
  hyperlinksModel: TerminalHyperlinksModel,
  applier: EditorTextDecorationApplier,
  event: TerminalHyperlinksOutputEvent,
  onLinkClicked: (TerminalHyperlinkId, EditorMouseEvent) -> Unit,
) {
  when (event) {
    is TerminalHyperlinksOutputEvent.HyperlinksUpdated -> {
      val removeFromOffset = event.removeFromOffset
      if (removeFromOffset != null) {
        val removed = hyperlinksModel.removeHyperlinks(removeFromOffset)
        applier.removeDecorations(removed.map { it.toPlatformId() })
      }

      val hyperlinks = event.hyperlinks.map { it.toFilterResultInfo() }
      hyperlinksModel.addHyperlinks(hyperlinks)
      val decorations = hyperlinks.mapNotNull {
        it.toEditorDecoration(outputModel, onLinkClicked)
      }
      applier.addDecorations(decorations)
    }
  }
}

private fun TerminalFilterResultInfo.toEditorDecoration(
  outputModel: TerminalOutputModel,
  onLinkClicked: (TerminalHyperlinkId, EditorMouseEvent) -> Unit,
): EditorTextDecoration? {
  return when (this) {
    is TerminalHyperlinkInfo -> {
      buildHyperlink(
        id = id.toPlatformId(),
        startOffset = TerminalOffset.of(absoluteStartOffset).toRelative(outputModel),
        endOffset = TerminalOffset.of(absoluteEndOffset).toRelative(outputModel),
        action = { onLinkClicked(id, it) },
      ) {
        attributes = style
        followedAttributes = followedStyle
        hoveredAttributes = hoveredStyle
        isInvisibleLink = this@toEditorDecoration.isInvisibleLink
        layer = this@toEditorDecoration.layer
      }
    }
    is TerminalHighlightingInfo -> style?.let { style ->
      buildHighlighting(
        id = id.toPlatformId(),
        startOffset = TerminalOffset.of(absoluteStartOffset).toRelative(outputModel),
        endOffset = TerminalOffset.of(absoluteEndOffset).toRelative(outputModel),
        attributes = style,
      ) {
        layer = layer
      }
    }
    is TerminalInlayInfo -> inlayProvider?.let { inlayProvider ->
      buildInlay(
        id = id.toPlatformId(),
        offset = TerminalOffset.of(absoluteEndOffset).toRelative(outputModel), // for inlays the end offset corresponds to the position
        inlayProvider = inlayProvider,
      )
    }
  }
}

private class TerminalOutputModelChangesTracker(
  private val outputModel: TerminalOutputModel,
  parentDisposable: Disposable,
) {
  // Variables should be accessed only from EDT
  private var contentChanged: Boolean = true
  private var firstChangedLine: TerminalLineIndex = outputModel.firstLineIndex

  init {
    outputModel.addListener(parentDisposable, object : TerminalOutputModelListener {
      override fun afterContentChanged(event: TerminalContentChangeEvent) {
        if (!event.isTrimming) {
          val line = event.model.getLineByOffset(event.offset)
          firstChangedLine = minOf(firstChangedLine, line)
        }
        contentChanged = true
      }
    })
  }

  @RequiresEdt
  fun getFirstChangedLineAndReset(): TerminalLineIndex? {
    if (!contentChanged) return null

    contentChanged = false
    // The stored line may be below `outputModel.firstLineIndex` if trim happened after it was recorded.
    // Clamp it, so callers never see a line that no longer exists in the model.
    val line = maxOf(firstChangedLine, outputModel.firstLineIndex)
    firstChangedLine = outputModel.lastLineIndex
    return line
  }
}

private val LOG = fileLogger()