// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.commands

import com.intellij.codeInsight.inline.completion.InlineCompletion
import com.intellij.codeInsight.inline.completion.InlineCompletionEvent
import com.intellij.codeInsight.inline.completion.InlineCompletionEventAdapter
import com.intellij.codeInsight.inline.completion.InlineCompletionEventType
import com.intellij.codeInsight.inline.completion.InlineCompletionHandler
import com.intellij.codeInsight.inline.completion.session.InlineCompletionSession
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.ui.playback.commands.PlaybackCommandCoroutineAdapter
import com.intellij.openapi.util.ActionCallback
import com.intellij.openapi.util.await
import com.jetbrains.performancePlugin.PerformanceTestSpan
import com.jetbrains.performancePlugin.utils.ActionCallbackProfilerStopper
import io.opentelemetry.api.trace.Span
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.annotations.NonNls
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

private val LOG = logger<CallInlineCompletionCommand>()

/**
 * CallInlineCompletionCommand is responsible for invoking inline completion
 * within a text editor. It measures `callInlineCompletionShow` or `callInlineCompletionHide` metrics.
 *
 * Syntax: `%callInlineCompletionCommand [warmup <timeoutMs>] [requireSuggestion]`.
 * - `warmup <timeoutMs>`: before the measured call, the command calls inline completion again and again without spans.
 *   It stops when a suggestion shows. It fails when no suggestion shows before the timeout.
 *   Use it when the provider needs time to get ready, for example, to load a local model.
 * - `requireSuggestion`: the command fails when the measured call completes without a shown suggestion.
 */
class CallInlineCompletionCommand(text: String, line: Int) : PlaybackCommandCoroutineAdapter(text, line) {
  companion object {
    const val PREFIX: @NonNls String = CMD_PREFIX + "callInlineCompletionCommand"
    const val SPAN_NAME: @NonNls String = "callInlineCompletion"
    const val ON_SHOW_SPAN_NAME: @NonNls String = SPAN_NAME + "OnShow"
    const val ON_COMPLETION_SPAN_NAME: @NonNls String = SPAN_NAME + "OnCompletion"
    const val ON_HIDE_SPAN_NAME: @NonNls String = SPAN_NAME + "OnHide"

    const val WARMUP_ARG: @NonNls String = "warmup"
    const val REQUIRE_SUGGESTION_ARG: @NonNls String = "requireSuggestion"

    private val WARMUP_ATTEMPT_TIMEOUT: Duration = 30.seconds
    private val WARMUP_RETRY_DELAY: Duration = 1.seconds
  }

  override suspend fun doExecute(context: PlaybackContext) {
    val args = extractCommandArgument(PREFIX).split(" ").filter { it.isNotBlank() }
    val warmupTimeout = args.indexOf(WARMUP_ARG).takeIf { it >= 0 }?.let { index ->
      args.getOrNull(index + 1)?.toLongOrNull()?.milliseconds ?: error("Missing timeout in ms after '$WARMUP_ARG'")
    }
    val requireSuggestion = REQUIRE_SUGGESTION_ARG in args

    val editor = FileEditorManager.getInstance(context.project).selectedTextEditor ?: error("No editor")
    val handler = InlineCompletion.getHandlerOrNull(editor) ?: error("No inline completion handler")

    if (warmupTimeout != null) {
      warmUp(editor, handler, warmupTimeout)
    }

    val actionCallback: ActionCallback = ActionCallbackProfilerStopper()
    var isShown = false
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
        isShown = true
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
      invokeDirectCall(editor, handler)
      actionCallback.await()
    }
    finally {
      handler.removeEventListener(listener)
    }

    if (!isShown) {
      val message = "Inline completion completed without a shown suggestion. The '$ON_COMPLETION_SPAN_NAME' span measures an empty result."
      if (requireSuggestion) error(message) else LOG.warn(message)
    }
  }

  private suspend fun warmUp(editor: Editor, handler: InlineCompletionHandler, timeout: Duration) {
    val start = TimeSource.Monotonic.markNow()
    var attempt = 0
    while (start.elapsedNow() < timeout) {
      attempt++
      if (tryShowSuggestion(editor, handler)) {
        LOG.info("Inline completion warm-up finished after $attempt attempt(s) in ${start.elapsedNow()}")
        handler.cancel()
        return
      }
      delay(WARMUP_RETRY_DELAY)
    }
    error("Inline completion warm-up failed: no suggestion is shown after $attempt attempt(s) in $timeout")
  }

  private suspend fun tryShowSuggestion(editor: Editor, handler: InlineCompletionHandler): Boolean {
    val completed = CompletableDeferred<Unit>()
    var isShown = false
    val listener = object : InlineCompletionEventAdapter {
      override fun onShow(event: InlineCompletionEventType.Show) {
        isShown = true
      }

      override fun onCompletion(event: InlineCompletionEventType.Completion) {
        completed.complete(Unit)
      }
    }
    try {
      handler.addEventListener(listener)
      invokeDirectCall(editor, handler)
      withTimeoutOrNull(WARMUP_ATTEMPT_TIMEOUT) { completed.await() }
    }
    finally {
      handler.removeEventListener(listener)
    }
    return isShown
  }

  /**
   * The handler ignores the event without any inline completion event when no provider is enabled for it.
   * In this case, the command fails at once instead of a wait for `onCompletion` that never comes.
   */
  private suspend fun invokeDirectCall(editor: Editor, handler: InlineCompletionHandler) {
    withContext(Dispatchers.EDT) {
      handler.invokeEvent(InlineCompletionEvent.DirectCall(editor, editor.caretModel.currentCaret))
      if (InlineCompletionSession.getOrNull(editor) == null) {
        error("Inline completion did not start a session: no inline completion provider is enabled for this editor. " +
              "Check the provider settings, the file language and the hardware compatibility of the provider.")
      }
    }
  }
}