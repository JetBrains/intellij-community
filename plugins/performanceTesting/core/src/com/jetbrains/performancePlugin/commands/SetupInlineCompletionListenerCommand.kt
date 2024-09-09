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
    private val listeners: MutableMap<Editor, InlineCompletionEventListener> = mutableMapOf()
  }

  override suspend fun doExecute(context: PlaybackContext) {
    val project = context.project
    val editor = readAction { FileEditorManager.getInstance(project).getSelectedTextEditor() ?: error("editor is null") }
    val handler = InlineCompletion.getHandlerOrNull(editor) ?: throw IllegalStateException("InlineCompletion handler is null")
    val currentOTContext = Context.current()
    if (listeners.containsKey(editor)) {
      handler.removeEventListener(listeners[editor]!!)
    }
    val l = object : InlineCompletionEventAdapter {
      private var spanShow: Span? = null
      private var spanHide: Span? = null
      override fun onRequest(event: InlineCompletionEventType.Request) {
        currentOTContext.makeCurrent().use {
          spanShow = PerformanceTestSpan.TRACER.spanBuilder(SPAN_NAME + "Show").startSpan()
          spanHide = PerformanceTestSpan.TRACER.spanBuilder(SPAN_NAME + "Hide").startSpan()
        }
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