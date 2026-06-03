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
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.project.projectId
import com.intellij.platform.util.coroutines.childScope
import com.intellij.terminal.frontend.view.impl.toRelative
import com.intellij.util.AwaitCancellationAndInvoke
import com.intellij.util.asDisposable
import com.intellij.util.awaitCancellationAndInvoke
import com.intellij.util.concurrency.annotations.RequiresEdt
import fleet.rpc.client.durable
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.block.reworked.TerminalSessionModel
import org.jetbrains.plugins.terminal.hyperlinks.TerminalFilterResultInfo
import org.jetbrains.plugins.terminal.hyperlinks.TerminalHighlightingInfo
import org.jetbrains.plugins.terminal.hyperlinks.TerminalHyperlinkId
import org.jetbrains.plugins.terminal.hyperlinks.TerminalHyperlinkInfo
import org.jetbrains.plugins.terminal.hyperlinks.TerminalHyperlinksModel
import org.jetbrains.plugins.terminal.hyperlinks.TerminalInlayInfo
import org.jetbrains.plugins.terminal.hyperlinks.TerminalOutputContentUpdate
import org.jetbrains.plugins.terminal.hyperlinks.rpc.TerminalCreateHyperlinksSessionRequest
import org.jetbrains.plugins.terminal.hyperlinks.rpc.TerminalHyperlinksRemoteApi
import org.jetbrains.plugins.terminal.hyperlinks.rpc.TerminalHyperlinksSessionRemoteApi
import org.jetbrains.plugins.terminal.hyperlinks.session.TerminalHyperlinkClickedEvent
import org.jetbrains.plugins.terminal.hyperlinks.session.TerminalHyperlinksInputEvent
import org.jetbrains.plugins.terminal.hyperlinks.session.TerminalHyperlinksOutputEvent
import org.jetbrains.plugins.terminal.hyperlinks.session.TerminalHyperlinksSession
import org.jetbrains.plugins.terminal.hyperlinks.session.TerminalHyperlinksSessionId
import org.jetbrains.plugins.terminal.hyperlinks.session.toDto
import org.jetbrains.plugins.terminal.hyperlinks.session.toFilterResultInfo
import org.jetbrains.plugins.terminal.hyperlinks.toPlatformId
import org.jetbrains.plugins.terminal.view.TerminalContentChangeEvent
import org.jetbrains.plugins.terminal.view.TerminalLineIndex
import org.jetbrains.plugins.terminal.view.TerminalOffset
import org.jetbrains.plugins.terminal.view.TerminalOutputModel
import org.jetbrains.plugins.terminal.view.TerminalOutputModelListener
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * @param eelDescriptor environment where the terminal process is running.
 */
@ApiStatus.Internal
fun installHyperlinksProcessing(
  project: Project,
  outputModel: TerminalOutputModel,
  editor: EditorEx,
  sessionModel: TerminalSessionModel,
  eelDescriptor: EelDescriptor,
  coroutineScope: CoroutineScope,
): FrontendTerminalHyperlinkFacade {
  // The modification stamp of the most recent highlighting task whose
  // `TerminalHyperlinksOutputEvent.TaskFinished` event has been observed.
  val lastFinishedTaskStamp = MutableStateFlow(0L)
  val sessionIdDeferred = CompletableDeferred<TerminalHyperlinksSessionId>(coroutineScope.coroutineContext.job)
  val applier = createEditorTextDecorationApplier(editor, coroutineScope.asDisposable())

  coroutineScope.launch {
    processHyperlinks(project, outputModel, sessionModel, eelDescriptor, sessionIdDeferred, applier, lastFinishedTaskStamp)
  }

  return FrontendTerminalHyperlinkFacade(sessionIdDeferred, applier, lastFinishedTaskStamp)
}

private suspend fun processHyperlinks(
  project: Project,
  outputModel: TerminalOutputModel,
  sessionModel: TerminalSessionModel,
  eelDescriptor: EelDescriptor,
  sessionIdDeferred: CompletableDeferred<TerminalHyperlinksSessionId>,
  applier: EditorTextDecorationApplier,
  lastFinishedTaskStamp: MutableStateFlow<Long>,
) = coroutineScope {
  val scope = this
  val session = createHyperlinksSession(
    project = project,
    eelDescriptor = eelDescriptor,
    coroutineScope = scope.childScope("FrontendTerminalHyperlinksSession")
  )
  sessionIdDeferred.complete(session.id)

  val outputModelChangesTracker = TerminalOutputModelChangesTracker(outputModel, parentDisposable = this.asDisposable())

  launch(CoroutineName("Output model tracking")) {
    trackOutputModelChanges(outputModel, sessionModel, outputModelChangesTracker, session.inputEventsSink)
  }
  // Can't use Dispatchers.UI because editor can require locks
  launch(Dispatchers.EDT + ModalityState.any().asContextElement() + CoroutineName("Results processing")) {
    processHyperlinkResults(
      debugName = "Frontend#${session.id.id}",
      outputModel = outputModel,
      applier = applier,
      outputModelChangesTracker = outputModelChangesTracker,
      hyperlinkUpdatesChannel = session.hyperlinkUpdatesChannel,
      inputEventsSink = session.inputEventsSink,
      lastFinishedTaskStamp = lastFinishedTaskStamp,
      onLinkClicked = { id, mouseEvent ->
        scope.launch {
          session.handleHyperlinkClick(TerminalHyperlinkClickedEvent(id, mouseEvent))
        }
      }
    )
  }
}

@OptIn(AwaitCancellationAndInvoke::class)
private suspend fun createHyperlinksSession(
  project: Project,
  eelDescriptor: EelDescriptor,
  coroutineScope: CoroutineScope,
): TerminalHyperlinksSession {
  val sessionId = durable {
    val request = TerminalCreateHyperlinksSessionRequest(project.projectId(), eelDescriptor)
    TerminalHyperlinksRemoteApi.getInstance().createNewSession(request)
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
  sessionModel: TerminalSessionModel,
  tracker: TerminalOutputModelChangesTracker,
  sink: SendChannel<TerminalHyperlinksInputEvent>,
) = coroutineScope {
  // Await the initial working directory received from the process
  // to send it to the hyperlinks' backend before any terminal content.
  // Otherwise, there can be a race, and some part of the initial output
  // may be highlighted without taking the working directory into account.
  val initialDirectory = withTimeoutOrNull(5.seconds) {
    sessionModel.terminalState.map { it.currentDirectory }.first { it != null }
  }
  if (initialDirectory != null) {
    sink.send(TerminalHyperlinksInputEvent.WorkingDirectoryChanged(initialDirectory))
  }

  // Send working directory change events
  launch {
    var currentDirectory: String? = initialDirectory
    sessionModel.terminalState.collect { state ->
      val newDirectory = state.currentDirectory ?: return@collect
      if (newDirectory != currentDirectory) {
        currentDirectory = newDirectory
        sink.send(TerminalHyperlinksInputEvent.WorkingDirectoryChanged(newDirectory))
      }
    }
  }

  // Send content updates to the backend periodically.
  while (true) {
    val update = withContext(Dispatchers.UI + ModalityState.any().asContextElement()) {
      try {
        getContentUpdate(outputModel, tracker)
      }
      catch (e: Exception) {
        LOG.error("Error when collecting content update", e)
        null
      }
    }

    if (update != null) {
      sink.send(TerminalHyperlinksInputEvent.ContentUpdated(update.toDto()))
    }

    delay(HYPERLINKS_OUTPUT_MODEL_FLUSH_DELAY)
  }
}

@RequiresEdt
private fun getContentUpdate(
  outputModel: TerminalOutputModel,
  tracker: TerminalOutputModelChangesTracker,
): TerminalOutputContentUpdate? {
  val firstChangedLine = tracker.getFirstChangedLineAndReset() ?: return null
  val startOffset = outputModel.getStartOfLine(firstChangedLine)
  return TerminalOutputContentUpdate(
    charsSequence = outputModel.getText(startOffset, outputModel.endOffset),
    startLine = firstChangedLine,
    endLine = outputModel.lastLineIndex,
    startOffset = startOffset,
    trimStartLine = outputModel.firstLineIndex,
    trimStartOffset = outputModel.startOffset,
    modificationStamp = outputModel.modificationStamp,
  )
}

private suspend fun processHyperlinkResults(
  debugName: String,
  outputModel: TerminalOutputModel,
  applier: EditorTextDecorationApplier,
  outputModelChangesTracker: TerminalOutputModelChangesTracker,
  hyperlinkUpdatesChannel: ReceiveChannel<TerminalHyperlinksOutputEvent>,
  inputEventsSink: SendChannel<TerminalHyperlinksInputEvent>,
  lastFinishedTaskStamp: MutableStateFlow<Long>,
  onLinkClicked: (TerminalHyperlinkId, EditorMouseEvent) -> Unit,
) {
  val hyperlinksModel = TerminalHyperlinksModel(
    debugName = debugName,
    trimOffset = { outputModel.startOffset },
  )

  for (event in hyperlinkUpdatesChannel) {
    try {
      processHyperlinksOutputEvent(
        outputModel = outputModel,
        hyperlinksModel = hyperlinksModel,
        applier = applier,
        outputModelChangesTracker = outputModelChangesTracker,
        inputEventsSink = inputEventsSink,
        event = event,
        lastFinishedTaskStamp = lastFinishedTaskStamp,
        onLinkClicked = onLinkClicked
      )
    }
    catch (e: CancellationException) {
      throw e
    }
    catch (e: Exception) {
      LOG.error("Error when processing hyperlinks update: $event", e)
    }
  }
}

private suspend fun processHyperlinksOutputEvent(
  outputModel: TerminalOutputModel,
  hyperlinksModel: TerminalHyperlinksModel,
  applier: EditorTextDecorationApplier,
  outputModelChangesTracker: TerminalOutputModelChangesTracker,
  inputEventsSink: SendChannel<TerminalHyperlinksInputEvent>,
  event: TerminalHyperlinksOutputEvent,
  lastFinishedTaskStamp: MutableStateFlow<Long>,
  onLinkClicked: (TerminalHyperlinkId, EditorMouseEvent) -> Unit,
) {
  when (event) {
    is TerminalHyperlinksOutputEvent.HyperlinksUpdated -> {
      processHyperlinksUpdatedEvent(
        outputModel = outputModel,
        hyperlinksModel = hyperlinksModel,
        applier = applier,
        outputModelChangesTracker = outputModelChangesTracker,
        event = event,
        onLinkClicked = onLinkClicked
      )
    }
    is TerminalHyperlinksOutputEvent.TaskFinished -> {
      lastFinishedTaskStamp.value = event.documentModificationStamp
    }
    is TerminalHyperlinksOutputEvent.FiltersUpdated -> {
      // Send the all-existing content to re-process it.
      val contentUpdate = TerminalOutputContentUpdate(
        charsSequence = outputModel.getText(outputModel.startOffset, outputModel.endOffset),
        startLine = outputModel.firstLineIndex,
        endLine = outputModel.lastLineIndex,
        startOffset = outputModel.startOffset,
        trimStartLine = outputModel.firstLineIndex,
        trimStartOffset = outputModel.startOffset,
        modificationStamp = outputModel.modificationStamp,
      )
      val event = TerminalHyperlinksInputEvent.ContentUpdated(contentUpdate.toDto())
      inputEventsSink.send(event)
    }
  }
}

private fun processHyperlinksUpdatedEvent(
  outputModel: TerminalOutputModel,
  hyperlinksModel: TerminalHyperlinksModel,
  applier: EditorTextDecorationApplier,
  outputModelChangesTracker: TerminalOutputModelChangesTracker,
  event: TerminalHyperlinksOutputEvent.HyperlinksUpdated,
  onLinkClicked: (TerminalHyperlinkId, EditorMouseEvent) -> Unit,
) {
  val removeFromOffset = event.removeFromOffset
  if (removeFromOffset != null) {
    val removed = hyperlinksModel.removeHyperlinks(removeFromOffset)
    applier.removeDecorations(removed.map { it.toPlatformId() })
  }

  val modelStartOffset = outputModel.startOffset.toAbsolute()
  val firstChangedOffset = outputModelChangesTracker.getFirstChangedOffsetSinceStamp(event.documentModificationStamp).toAbsolute()
  val allHyperlinks = event.hyperlinks
    .asSequence()
    .map { it.toFilterResultInfo() }
    .filter { it.absoluteStartOffset >= modelStartOffset }  // Filter out trimmed hyperlinks
    .filter { it.absoluteEndOffset <= firstChangedOffset }   // Filter out hyperlinks in the range that was changed during links' calculation
    .toList()

  // Add only hyperlinks that can be transformed into decorations
  val hyperlinks = ArrayList<TerminalFilterResultInfo>(allHyperlinks.size)
  val decorations = ArrayList<EditorTextDecoration>(allHyperlinks.size)
  for (link in allHyperlinks) {
    val decoration = link.toEditorDecoration(outputModel, onLinkClicked) ?: continue
    decorations.add(decoration)
    hyperlinks.add(link)
  }
  hyperlinksModel.addHyperlinks(hyperlinks)
  applier.addDecorations(decorations)
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

  /** Ordered by [ChangeInfo.modificationStamp] */
  private val changesHistory = ArrayDeque<ChangeInfo>(initialCapacity = MAX_CHANGES_HISTORY_LENGTH)

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

  /**
   * Returns the first changed line index since the last call.
   */
  @RequiresEdt
  fun getFirstChangedLineAndReset(): TerminalLineIndex? {
    if (!contentChanged) return null

    // The stored line may be below `outputModel.firstLineIndex` if trim happened after it was recorded.
    // Clamp it, so callers never see a line that no longer exists in the model.
    val line = maxOf(firstChangedLine, outputModel.firstLineIndex)
    recordChange(line)

    contentChanged = false
    firstChangedLine = outputModel.lastLineIndex
    return line
  }

  /**
   * Analyzes the output model changes history to find the range of content that was changed since the [modificationStamp].
   * Returns the start of this range - the first changed character offset.
   */
  @RequiresEdt
  fun getFirstChangedOffsetSinceStamp(modificationStamp: Long): TerminalOffset {
    val searchResult = changesHistory.binarySearch { changeInfo ->
      if (changeInfo.modificationStamp <= modificationStamp) -1 else 1
    }
    val nextChangeIndex = -searchResult - 1
    if (nextChangeIndex == changesHistory.size) {
      // No changes after the specified stamp, return the end of the model
      return outputModel.endOffset
    }

    return changesHistory.subList(nextChangeIndex, changesHistory.size).minOf { it.startOffset }
  }

  private fun recordChange(startLine: TerminalLineIndex) {
    val offset = outputModel.getStartOfLine(startLine)
    val changeInfo = ChangeInfo(offset, outputModel.modificationStamp)
    changesHistory.addLast(changeInfo)
    while (changesHistory.size > MAX_CHANGES_HISTORY_LENGTH) {
      changesHistory.removeFirst()
    }
  }

  private data class ChangeInfo(
    // The offset of the first changed character
    val startOffset: TerminalOffset,
    // The modification stamp of the document at the moment of registering the change
    val modificationStamp: Long,
  )
}

@ApiStatus.Internal
val HYPERLINKS_OUTPUT_MODEL_FLUSH_DELAY: Duration = 20.milliseconds

/**
 * Covers the changes in the output model history
 * for [MAX_CHANGES_HISTORY_LENGTH] * [HYPERLINKS_OUTPUT_MODEL_FLUSH_DELAY] = 2 seconds at least
 */
private const val MAX_CHANGES_HISTORY_LENGTH = 100

private val LOG = fileLogger()