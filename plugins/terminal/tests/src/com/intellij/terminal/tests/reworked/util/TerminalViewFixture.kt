// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.tests.reworked.util

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.platform.util.coroutines.childScope
import com.intellij.terminal.frontend.view.activeOutputModel
import com.intellij.terminal.frontend.view.impl.TerminalViewImpl
import com.intellij.testFramework.EditorTestUtil
import com.intellij.testFramework.dispatchAllEventsInIdeEventQueue
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.cancel
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.plugins.terminal.JBTerminalSystemSettingsProvider
import org.jetbrains.plugins.terminal.TerminalEmulatorType
import org.jetbrains.plugins.terminal.util.terminalProjectScope
import org.jetbrains.plugins.terminal.view.TerminalContentChangeEvent
import org.jetbrains.plugins.terminal.view.TerminalCursorOffsetChangeEvent
import org.jetbrains.plugins.terminal.view.TerminalOutputModel
import org.jetbrains.plugins.terminal.view.TerminalOutputModelListener
import org.jetbrains.plugins.terminal.view.impl.MutableTerminalOutputModel
import kotlin.coroutines.resume
import kotlin.math.ceil
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * A real [TerminalViewImpl] connected to a loopback-backed production `TerminalSession` - no real shell process -
 * so a test can drive the whole frontend pipeline (session, output model, UI) the way production does.
 *
 * [emulatorType] selects the VT emulator ([TerminalEmulatorType.JediTerm] or [TerminalEmulatorType.Ghostty])
 * driving the session.
 */
internal class TerminalViewFixture(project: Project, emulatorType: TerminalEmulatorType) : AutoCloseable {
  private val scope = terminalProjectScope(project).childScope("TerminalViewFixture")

  val connector: LoopbackTtyConnector
  val view: TerminalViewImpl

  init {
    val (session, connector) = TerminalSessionTestUtil.createLoopbackTerminalSession(project, scope, emulatorType)
    this.connector = connector

    view = TerminalViewImpl(project, JBTerminalSystemSettingsProvider(), null, scope)
    view.connectToSession(session)
  }

  /**
   * Resizes the terminal to fit exactly [columns] by [rows] characters, the way a real window resize would:
   * [TerminalViewImpl] itself notices the new size and reports it to the session instead of a test constructing a resize event by hand.
   */
  @RequiresEdt
  fun resize(columns: Int, rows: Int) {
    val editor = if (view.isAlternateScreenBuffer) view.alternateBufferEditor else view.outputEditor
    val characterGrid = checkNotNull(editor.characterGrid) { "Character grid is not initialized" }
    EditorTestUtil.setEditorVisibleSizeInPixels(
      editor,
      ceil(columns * characterGrid.charWidth).toInt(),
      rows * editor.lineHeight
    )

    // The viewport change above isn't a resize of the top component TerminalViewImpl listens on, so nudge that
    // separately; its listener re-reads the (already correct) grid size from the editor and reports it, exactly
    // as it would for a real window resize.
    val panel = view.component
    panel.setSize(panel.width + 1, panel.height + 1)
    dispatchAllEventsInIdeEventQueue()
  }

  /**
   * Suspends until [condition] holds for [model] (by default, the currently active output buffer), checking
   * immediately and then again after every model change. Returns `false` if [timeout] elapses first.
   */
  suspend fun awaitOutputModelState(
    model: TerminalOutputModel = view.activeOutputModel(),
    timeout: Duration = 10.seconds,
    condition: (TerminalOutputModel) -> Boolean,
  ): Boolean {
    val result = withTimeoutOrNull(timeout) {
      suspendCancellableCoroutine { continuation ->
        if (condition(model)) {
          continuation.resume(Unit)
          return@suspendCancellableCoroutine
        }

        val disposable = Disposer.newDisposable()
        continuation.invokeOnCancellation { Disposer.dispose(disposable) }

        fun check() {
          if (condition(model)) {
            Disposer.dispose(disposable)
            continuation.resume(Unit)
          }
        }

        model.addListener(disposable, object : TerminalOutputModelListener {
          override fun afterContentChanged(event: TerminalContentChangeEvent) = check()
          override fun cursorOffsetChanged(event: TerminalCursorOffsetChangeEvent) = check()
        })
      }
    }
    return result != null
  }

  /** Like [awaitOutputModelState], but the condition is that [model] renders as [pattern]. */
  @Suppress("unused") // part of the fixture's contract; no current caller needs a styled/hyperlink pattern yet.
  suspend fun awaitOutputModelMatches(
    pattern: TerminalOutputPattern,
    model: TerminalOutputModel = view.activeOutputModel(),
    timeout: Duration = 10.seconds,
  ): Boolean {
    return awaitOutputModelState(model, timeout) {
      (it as MutableTerminalOutputModel).matches(pattern)
    }
  }

  override fun close() {
    scope.cancel()
  }
}
