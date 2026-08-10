// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.inlay

import com.intellij.codeInsight.hints.InlayPresentationFactory
import com.intellij.codeInsight.hints.presentation.MouseButton
import com.intellij.codeInsight.hints.presentation.PresentationFactory
import com.intellij.codeInsight.hints.presentation.PresentationRenderer
import com.intellij.codeInsight.hints.presentation.mouseButton
import com.intellij.debugger.engine.JavaDebugProcess
import com.intellij.debugger.streams.core.StreamDebuggerBundle
import com.intellij.debugger.streams.core.StreamChainInlayState
import com.intellij.debugger.streams.core.ChainDetectionStateManager
import com.intellij.debugger.streams.core.action.TraceStreamRunner
import com.intellij.debugger.streams.core.statistics.TraceEntryPoint
import com.intellij.debugger.streams.shared.icons.DebuggerStreamsSharedIcons
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.JBPopupMenu.showByEvent
import com.intellij.openapi.util.Disposer
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebuggerManagerListener
import com.intellij.xdebugger.XSourcePosition
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Cursor
import java.awt.Point
import java.awt.event.MouseEvent
import java.util.concurrent.ConcurrentHashMap

/**
 * Renders the "Trace Current Stream Chain" inlay for JVM debug sessions by reactively consuming the shared, execution-aware
 * chain state from [ChainDetectionStateManager]. One session-scoped collector per session shows at most one inlay.
 */
@Service(Service.Level.PROJECT)
internal class StreamDebuggerInlayDisplay(private val project: Project, private val cs: CoroutineScope) {
  private val jobs = ConcurrentHashMap<XDebugSession, Job>()

  fun start(session: XDebugSession) {
    jobs.put(session, cs.launch(Dispatchers.Default) { collectInlayUpdates(session) })?.cancel()
  }

  fun stop(session: XDebugSession) {
    jobs.remove(session)?.cancel()
  }

  /**
   * Each new flow value cancels the previous [collectLatest] block (its `finally` disposes the previous inlay) before the
   * next one runs, so there is always exactly 0 or 1 inlay; the last inlay is disposed when the session-scoped job is cancelled.
   */
  private suspend fun collectInlayUpdates(session: XDebugSession) {
    ChainDetectionStateManager.getInstance(project).inlayStateFlow(session).collectLatest { state ->
      val visible = state as? StreamChainInlayState.Visible ?: return@collectLatest
      if (!isStreamDebuggerInlaysEnabled()) return@collectLatest
      val inlay = withContext(Dispatchers.EDT) { createInlay(session, visible.position) } ?: return@collectLatest
      try {
        awaitCancellation()
      }
      finally {
        withContext(NonCancellable + Dispatchers.EDT) { Disposer.dispose(inlay) }
      }
    }
  }

  private fun createInlay(session: XDebugSession, position: XSourcePosition): Inlay<PresentationRenderer>? {
    val editor = getEditor(position) ?: return null
    val lineEnd = editor.document.getLineEndOffset(position.line)
    val message = StreamDebuggerBundle.message("action.trace.stream.inlay.text")
    val renderer = with(PresentationFactory(editor)) {
      PresentationRenderer(
        roundWithBackgroundAndSmallInset(
          withCursorOnHover(
            mouseHandling(
              seq(
                smallScaledIcon(DebuggerStreamsSharedIcons.Stream_debugger),
                smallTextWithoutBackground(message)
              ),
              ClickHandler(session),
              null
            ),
            Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
          )
        )
      )
    }

    return editor.inlayModel.addAfterLineEndElement(lineEnd, false, renderer)
  }

  private fun getEditor(position: XSourcePosition): Editor? {
    val selectedEditor = FileEditorManager.getInstance(project).getSelectedEditor(position.file) ?: return null
    return (selectedEditor as? TextEditor)?.editor
  }

  private class ClickHandler(private val session: XDebugSession) : InlayPresentationFactory.ClickListener {
    override fun onClick(event: MouseEvent, translated: Point) {
      when (event.mouseButton) {
        MouseButton.Left -> TraceStreamRunner.getInstance(session.project).actionPerformed(session, TraceEntryPoint.INLAY_HINT)
        MouseButton.Right -> showByEvent(event, "StreamDebuggerInlayPopup",
                                         ActionManager.getInstance().getAction("StreamDebuggerInlayPopup") as ActionGroup)
        else -> Unit
      }
    }
  }

  companion object {
    fun getInstance(project: Project): StreamDebuggerInlayDisplay = project.service()
  }
}

internal class StreamDebuggerInlaySessionListener(private val project: Project) : XDebuggerManagerListener {
  override fun processStarted(debugProcess: XDebugProcess) {
    if (debugProcess is JavaDebugProcess) {
      StreamDebuggerInlayDisplay.getInstance(project).start(debugProcess.session)
    }
  }

  override fun processStopped(debugProcess: XDebugProcess) {
    if (debugProcess is JavaDebugProcess) {
      StreamDebuggerInlayDisplay.getInstance(project).stop(debugProcess.session)
    }
  }
}