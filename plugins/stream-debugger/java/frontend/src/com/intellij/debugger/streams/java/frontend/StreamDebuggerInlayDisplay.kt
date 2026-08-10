// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.java.frontend

import com.intellij.codeInsight.hints.InlayPresentationFactory
import com.intellij.codeInsight.hints.presentation.MouseButton
import com.intellij.codeInsight.hints.presentation.PresentationFactory
import com.intellij.codeInsight.hints.presentation.PresentationRenderer
import com.intellij.codeInsight.hints.presentation.mouseButton
import com.intellij.debugger.streams.shared.StreamChainInlayStateDto
import com.intellij.debugger.streams.shared.StreamDebuggerApi
import com.intellij.debugger.streams.shared.TraceEntryPoint
import com.intellij.debugger.streams.shared.icons.DebuggerStreamsSharedIcons
import com.intellij.java.debugger.impl.shared.SharedJavaDebuggerSession
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.JBPopupMenu.showByEvent
import com.intellij.openapi.util.Disposer
import com.intellij.platform.debugger.impl.shared.CustomDescriptorStateManager
import com.intellij.platform.debugger.impl.shared.proxy.XDebugSessionProxy
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.impl.XDebuggerManagerProxyListener
import com.intellij.xdebugger.impl.rpc.sourcePosition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Cursor
import java.awt.Point
import java.awt.event.MouseEvent

/**
 * Shows the "Trace Current Stream Chain" inlay for JVM debug sessions.
 * The backend publishes the chain state through [StreamDebuggerApi.getInlayState].
 * Each session has one collector, and each collector shows one inlay at most.
 */
internal class StreamDebuggerInlaySessionListener(private val project: Project) : XDebuggerManagerProxyListener {
  override fun sessionStarted(session: XDebugSessionProxy) {
    // The scope is canceled when the session stops. This stops the collector and disposes the last inlay.
    session.coroutineScope.launch(Dispatchers.Default) { collectInlayUpdates(project, session) }
  }
}

/**
 * Each new flow value cancels the previous [collectLatest] block before the next block starts.
 * The `finally` of the previous block disposes the previous inlay, so there is always exactly 0 or 1 inlay
 */
private suspend fun collectInlayUpdates(project: Project, session: XDebugSessionProxy) {
  StreamDebuggerApi.getInstance().getInlayState(session.id).collectLatest { state ->
    val visible = state as? StreamChainInlayStateDto.Visible ?: return@collectLatest
    if (!isStreamDebuggerInlaysEnabled()) return@collectLatest
    // The process descriptor is registered asynchronously, so the JVM check cannot be moved to `sessionStarted`.
    if (!isJavaSession(project, session)) return@collectLatest
    val inlay = withContext(Dispatchers.EDT) { createInlay(project, session, visible.position.sourcePosition()) }
                ?: return@collectLatest
    try {
      awaitCancellation()
    }
    finally {
      withContext(NonCancellable + Dispatchers.EDT) { Disposer.dispose(inlay) }
    }
  }
}

private fun isJavaSession(project: Project, session: XDebugSessionProxy): Boolean =
  CustomDescriptorStateManager.getInstance(project).getProcessDescriptorState(session.id) is SharedJavaDebuggerSession

private fun createInlay(project: Project, session: XDebugSessionProxy, position: XSourcePosition): Inlay<PresentationRenderer>? {
  val editor = getEditor(project, position) ?: return null
  val lineEnd = editor.document.getLineEndOffset(position.line)
  val message = JavaStreamDebuggerFrontendBundle.message("action.trace.stream.inlay.text")
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

private fun getEditor(project: Project, position: XSourcePosition): Editor? {
  val selectedEditor = FileEditorManager.getInstance(project).getSelectedEditor(position.file) ?: return null
  return (selectedEditor as? TextEditor)?.editor
}

private class ClickHandler(private val session: XDebugSessionProxy) : InlayPresentationFactory.ClickListener {
  override fun onClick(event: MouseEvent, translated: Point) {
    when (event.mouseButton) {
      MouseButton.Left -> session.coroutineScope.launch {
        StreamDebuggerApi.getInstance().showTraceDebuggerDialog(session.id, TraceEntryPoint.INLAY_HINT)
      }
      MouseButton.Right -> showByEvent(event, "StreamDebuggerInlayPopup",
                                       ActionManager.getInstance().getAction("StreamDebuggerInlayPopup") as ActionGroup)
      else -> Unit
    }
  }
}
