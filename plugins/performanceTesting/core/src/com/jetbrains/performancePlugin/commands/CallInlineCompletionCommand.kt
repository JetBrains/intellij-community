// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.commands

import com.intellij.codeInsight.inline.completion.InlineCompletion
import com.intellij.codeInsight.inline.completion.InlineCompletionEvent
import com.intellij.codeInsight.inline.completion.InlineCompletionEventAdapter
import com.intellij.codeInsight.inline.completion.InlineCompletionEventType
import com.intellij.openapi.application.EDT
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.ui.playback.commands.PlaybackCommandCoroutineAdapter
import com.intellij.openapi.util.ActionCallback
import com.intellij.openapi.util.await
import com.jetbrains.performancePlugin.PerformanceTestSpan
import com.jetbrains.performancePlugin.utils.ActionCallbackProfilerStopper
import io.opentelemetry.api.trace.Span
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.NonNls

/**
 * CallInlineCompletionCommand is responsible for invoking inline completion
 * within a text editor. It measures `callInlineCompletionShow` or `callInlineCompletionHide` metrics.
 */
class CallInlineCompletionCommand(text: String, line: Int) : PlaybackCommandCoroutineAdapter(text, line) {
  companion object {
    const val PREFIX: @NonNls String = CMD_PREFIX + "callInlineCompletionCommand"
    const val SPAN_NAME: @NonNls String = "callInlineCompletion"
    const val ON_SHOW_SPAN_NAME: @NonNls String = SPAN_NAME + "OnShow"
    const val ON_COMPLETION_SPAN_NAME: @NonNls String = SPAN_NAME + "OnCompletion"
    const val ON_HIDE_SPAN_NAME: @NonNls String = SPAN_NAME + "OnHide"
  }

  override suspend fun doExecute(context: PlaybackContext) {
    val actionCallback: ActionCallback = ActionCallbackProfilerStopper()

    val editor = blockingContext { FileEditorManager.getInstance(context.project).selectedTextEditor ?: error("No editor") }
    val handler = InlineCompletion.getHandlerOrNull(editor) ?: error("No inline completion handler")
    val listener = object : InlineCompletionEventAdapter {
      private var spanShow: Span? = null
      private var spanHide: Span? = null
      private var completionSpan: Span? = null
      override fun onCompletion(event: InlineCompletionEventType.Completion) {
        completionSpan?.end()
        completionSpan = null
        actionCallback.setDone()
      }

      override fun onShow(event: InlineCompletionEventType.Show) {
        spanShow?.end()
        spanHide = null
        spanShow = null
      }

      override fun onRequest(event: InlineCompletionEventType.Request) {
        spanShow = PerformanceTestSpan.TRACER.spanBuilder(ON_SHOW_SPAN_NAME).startSpan()
        spanHide = PerformanceTestSpan.TRACER.spanBuilder(ON_HIDE_SPAN_NAME).startSpan()
        completionSpan = PerformanceTestSpan.TRACER.spanBuilder(ON_COMPLETION_SPAN_NAME).startSpan()
      }

      override fun onHide(event: InlineCompletionEventType.Hide) {
        spanHide?.end()
        spanHide = null
        spanShow = null
      }
    }
    try {
      handler.addEventListener(listener)
      withContext(Dispatchers.EDT) {
        handler.invoke(InlineCompletionEvent.DirectCall(editor, editor.caretModel.currentCaret))
      }
      actionCallback.await()
    }
    finally {
      handler.removeEventListener(listener)
    }
  }
}