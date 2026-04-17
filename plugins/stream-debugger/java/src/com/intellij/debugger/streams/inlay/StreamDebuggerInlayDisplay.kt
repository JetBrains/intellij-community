// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.inlay

import com.intellij.codeInsight.hints.InlayPresentationFactory
import com.intellij.codeInsight.hints.presentation.MouseButton
import com.intellij.codeInsight.hints.presentation.PresentationFactory
import com.intellij.codeInsight.hints.presentation.PresentationRenderer
import com.intellij.codeInsight.hints.presentation.mouseButton
import com.intellij.debugger.engine.SuspendContextImpl
import com.intellij.debugger.streams.core.StreamDebuggerBundle
import com.intellij.debugger.streams.core.action.TraceStreamRunner
import com.intellij.debugger.streams.shared.ChainStatus
import com.intellij.openapi.application.EDT
import com.intellij.debugger.streams.shared.icons.DebuggerStreamsSharedIcons
import com.intellij.openapi.application.readAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.ui.JBPopupMenu.showByEvent
import com.intellij.util.AwaitCancellationAndInvoke
import com.intellij.util.awaitCancellationAndInvoke
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebugSessionListener
import com.intellij.xdebugger.XSourcePosition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.sun.jdi.event.ExceptionEvent
import java.awt.Cursor
import java.awt.Point
import java.awt.event.MouseEvent

internal class StreamDebuggerInlayDisplay(private val session: XDebugSession) : XDebugSessionListener {
  private val project = session.project

  @OptIn(AwaitCancellationAndInvoke::class)
  override fun sessionPaused() {
    if (!isStreamDebuggerInlaysEnabled()) return
    val topFramePosition = session.topFramePosition ?: return
    val suspendContext = session.suspendContext ?: return
    if ((suspendContext as? SuspendContextImpl)?.eventSet?.any { it is ExceptionEvent } == true) return
    val cs = suspendContext.coroutineScope ?: return
    cs.launch(Dispatchers.Default) {
      val chainStatus = readAction { TraceStreamRunner.getInstance(project).getChainStatus(session) }
      if (chainStatus == ChainStatus.FOUND) {
        withContext(Dispatchers.EDT) {
          val inlay = createInlay(topFramePosition)
          if (inlay != null) {
            cs.awaitCancellationAndInvoke(Dispatchers.EDT) {
              Disposer.dispose(inlay)
            }
          }
        }
      }
    }
  }

  private fun createInlay(position: XSourcePosition): Inlay<PresentationRenderer>? {
    val editor = getEditor(position) ?: return null
    val lineEnd = editor.document.getLineEndOffset(position.line)
    val message = StreamDebuggerBundle.message("action.trace.stream.inlay.text")
    val renderer = with(PresentationFactory(editor)) {
      PresentationRenderer(
        roundWithBackgroundAndSmallInset(
          mouseHandling(
            seq(
              smallScaledIcon(DebuggerStreamsSharedIcons.Stream_debugger),
              smallTextWithoutBackground(message)
            ),
            ClickHandler(session),
            HoverHandler(editor)
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
        MouseButton.Left -> TraceStreamRunner.getInstance(session.project).actionPerformed(session)
        MouseButton.Right -> showByEvent(event, "StreamDebuggerInlayPopup",
                                         ActionManager.getInstance().getAction("StreamDebuggerInlayPopup") as ActionGroup)
        else -> Unit
      }
    }
  }

  private class HoverHandler(private val editor: Editor) : InlayPresentationFactory.HoverListener {
    override fun onHover(event: MouseEvent, translated: Point) {
      (editor as? EditorEx)?.setCustomCursor(HoverHandler::class.java, Cursor.getPredefinedCursor(Cursor.HAND_CURSOR))
    }

    override fun onHoverFinished() {
      (editor as? EditorEx)?.setCustomCursor(HoverHandler::class.java, null)
    }
  }
}