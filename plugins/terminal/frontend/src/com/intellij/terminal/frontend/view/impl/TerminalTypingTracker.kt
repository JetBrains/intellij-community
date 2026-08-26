package com.intellij.terminal.frontend.view.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.terminal.frontend.view.TerminalKeyEvent
import com.intellij.terminal.frontend.view.TerminalKeyEventsListener
import com.intellij.terminal.frontend.view.TerminalView
import com.intellij.util.asDisposable
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.view.TerminalContentChangeEvent
import org.jetbrains.plugins.terminal.view.TerminalCursorOffsetChangeEvent
import org.jetbrains.plugins.terminal.view.TerminalOutputModelListener
import org.jetbrains.plugins.terminal.view.impl.MutableTerminalOutputModel
import org.jetbrains.plugins.terminal.view.shellIntegration.TerminalShellIntegration

/**
 * Matches terminal key events against terminal output model updates to determine whether typed input was
 * confirmed by the real shell output or must be treated as a mismatch. See [TerminalTypingListener].
 *
 * **Note** that it works only when [TerminalView.shellIntegrationDeferred] is available.
 * And reports events only when the user is typing the command text.
 * It doesn't report when any application is running in the terminal.
 */
@ApiStatus.Internal
interface TerminalTypingTracker {
  fun addTypingListener(parentDisposable: Disposable, listener: TerminalTypingListener)
}

/**
 * A listener for [TerminalTypingTracker] events.
 *
 * Called synchronously, on the EDT in the same invocation event that fed the tracker the key event or output model update
 * that produced the [TerminalTypingEvent].
 */
@ApiStatus.Internal
interface TerminalTypingListener {
  @RequiresEdt
  fun onTypingEvent(event: TerminalTypingEvent)
}

@ApiStatus.Internal
sealed interface TerminalTypingEvent {
  /**
   * [keyEvent] was confirmed by matching shell output.
   *
   * [TerminalKeyEvent.cursorOffset] is the offset where this keystroke's effect was predicted to land,
   * which can differ from the offset the original event carried if other keystrokes were still pending
   * confirmation when this one was typed.
   */
  data class Confirmed(val keyEvent: TerminalKeyEvent) : TerminalTypingEvent

  /**
   * The shell output diverged from typings, or our logic failed to match typings with the output.
   * The command text changed unexpectedly or typing wasn't confirmed by the output in meaningful time.
   */
  data object Mismatch : TerminalTypingEvent
}


internal fun installTypingTracker(
  project: Project,
  terminalView: TerminalView,
  model: MutableTerminalOutputModel,
  shellIntegration: TerminalShellIntegration,
  coroutineScope: CoroutineScope,
): TerminalTypingTracker {
  val tracker = TerminalTypingTrackerImpl(project, model, shellIntegration, coroutineScope)
  val parentDisposable = coroutineScope.asDisposable()
  terminalView.addKeyEventsListener(parentDisposable, object : TerminalKeyEventsListener {
    override fun beforeKeyEvent(event: TerminalKeyEvent): Boolean {
      tracker.handleKeyEvent(event)
      return false  // do not consume the event
    }
  })
  model.addListener(parentDisposable, object : TerminalOutputModelListener {
    override fun afterContentChanged(event: TerminalContentChangeEvent) {
      tracker.handleContentChanged()
    }

    override fun cursorOffsetChanged(event: TerminalCursorOffsetChangeEvent) {
      tracker.handleCursorOffsetChanged()
    }
  })
  return tracker
}
