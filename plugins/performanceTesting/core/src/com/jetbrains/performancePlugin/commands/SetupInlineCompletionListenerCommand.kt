// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.commands

import com.intellij.codeInsight.inline.completion.InlineCompletion
import com.intellij.codeInsight.inline.completion.InlineCompletionEventAdapter
import com.intellij.codeInsight.inline.completion.InlineCompletionEventListener
import com.intellij.codeInsight.inline.completion.InlineCompletionEventType
import com.intellij.openapi.application.readAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.ui.playback.commands.PlaybackCommandCoroutineAdapter
import com.jetbrains.performancePlugin.PerformanceTestSpan
import io.opentelemetry.api.trace.Span
import io.opentelemetry.context.Context
import org.jetbrains.annotations.NonNls
import kotlin.collections.mutableMapOf

/**
 * InlineCompletionCommand is responsible for setting up an inline completion listener for the editor
 * and handling specific inline completion events such as request, show, and hide.
 *
 * Example: %setupInlineCompletionListener
 */
class SetupInlineCompletionListenerCommand(text: String, line: Int) : PlaybackCommandCoroutineAdapter(text, line) {
  companion object {
    const val PREFIX: @NonNls String = CMD_PREFIX + "setupInlineCompletionListener"
    const val SPAN_NAME: @NonNls String = "inlineCompletion"
    const val ON_SHOW_SPAN_NAME: @NonNls String = SPAN_NAME + "OnShow"
    const val ON_COMPLETION_SPAN_NAME: @NonNls String = SPAN_NAME + "OnCompletion"
    const val ON_HIDE_SPAN_NAME: @NonNls String = SPAN_NAME + "OnHide"
    private val listeners: MutableMap<Editor, InlineCompletionEventListener> = mutableMapOf()
  }

  override suspend fun doExecute(context: PlaybackContext) {
    val editor = readAction { FileEditorManager.getInstance(context.project).getSelectedTextEditor() ?: error("editor is null") }
    val handler = InlineCompletion.getHandlerOrNull(editor) ?: throw IllegalStateException("InlineCompletion handler is null")
    val currentOTContext = Context.current()

    listeners[editor]?.let(handler::removeEventListener)

    val l = object : InlineCompletionEventAdapter {
      private var spanShow: Span? = null
      private var spanHide: Span? = null
      private var completionSpan: Span? = null
      override fun onRequest(event: InlineCompletionEventType.Request) {
        currentOTContext.makeCurrent().use {
          spanShow = PerformanceTestSpan.TRACER.spanBuilder(ON_SHOW_SPAN_NAME).startSpan()
          spanHide = PerformanceTestSpan.TRACER.spanBuilder(ON_HIDE_SPAN_NAME).startSpan()
          completionSpan = PerformanceTestSpan.TRACER.spanBuilder(ON_COMPLETION_SPAN_NAME).startSpan()
        }
      }

      override fun onCompletion(event: InlineCompletionEventType.Completion) {
        completionSpan?.end()
        completionSpan = null
      }

      override fun onHide(event: InlineCompletionEventType.Hide) {
        currentOTContext.makeCurrent().use {
          spanHide?.end()
          spanHide = null
          spanShow = null
        }
      }

      override fun onShow(event: InlineCompletionEventType.Show) {
        currentOTContext.makeCurrent().use {
          spanShow?.end()
          spanShow = null
          spanHide = null
        }
      }
    }
    listeners[editor] = l
    handler.addEventListener(l)
  }
}