// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.tests.reworked.util

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.platform.util.coroutines.childScope
import com.intellij.terminal.frontend.view.activeOutputModel
import com.intellij.terminal.frontend.view.impl.TerminalViewImpl
import com.intellij.terminal.tests.reworked.util.TerminalTestUtil.text
import com.intellij.terminal.tests.reworked.util.TerminalViewFixture.Companion.BLOCKS_MODEL_POLL_INTERVAL
import com.intellij.testFramework.EditorTestUtil
import com.intellij.testFramework.dispatchAllEventsInIdeEventQueue
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.jediterm.core.util.TermSize
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.terminal.JBTerminalSystemSettingsProvider
import org.jetbrains.plugins.terminal.TerminalEmulatorType
import org.jetbrains.plugins.terminal.util.terminalProjectScope
import org.jetbrains.plugins.terminal.view.TerminalContentChangeEvent
import org.jetbrains.plugins.terminal.view.TerminalCursorOffsetChangeEvent
import org.jetbrains.plugins.terminal.view.TerminalOutputModel
import org.jetbrains.plugins.terminal.view.TerminalOutputModelListener
import org.jetbrains.plugins.terminal.view.impl.MutableTerminalOutputModel
import org.jetbrains.plugins.terminal.view.shellIntegration.TerminalBlocksModel
import kotlin.coroutines.resume
import kotlin.math.ceil
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
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
   *
   * Does not wait for the session to apply it. Use [resizeAndAwait] to assert a model state afterward.
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
   * Resizes to [columns] by [rows], then waits until the resize really landed: the session reported the
   * new size to the pty, and the active output model applied a content update for it.
   */
  suspend fun resizeAndAwait(columns: Int, rows: Int, timeout: Duration = 5.seconds) {
    val model = view.activeOutputModel()
    val contentChanged = CompletableDeferred<Unit>()
    val listenerDisposable = Disposer.newDisposable()
    model.addListener(listenerDisposable, object : TerminalOutputModelListener {
      override fun afterContentChanged(event: TerminalContentChangeEvent) {
        contentChanged.complete(Unit)
      }
    })
    try {
      resize(columns, rows)
      awaitReportedSize(TermSize(columns, rows), timeout)
      withTimeoutOrNull(timeout) { contentChanged.await() }
      ?: error("the resize to ${columns}x$rows reached the pty, but $model never applied an update for it")
    }
    finally {
      Disposer.dispose(listenerDisposable)
    }
  }

  /**
   * Suspends until the session asks the pty for [expected]. Drains the sizes reported before it, so an
   * earlier resize in the same case cannot satisfy this one.
   */
  private suspend fun awaitReportedSize(expected: TermSize, timeout: Duration) {
    val seen = ArrayList<TermSize>()
    val found = withTimeoutOrNull(timeout) {
      var reported: TermSize? = null
      while (reported != expected) {
        // The poll blocks, so it must not run on the EDT: the session controller applies its events
        // there, and a blocked EDT would stop the very update this waits for.
        reported = withContext(Dispatchers.IO) {
          connector.awaitResize(REPORTED_SIZE_POLL_INTERVAL.inWholeMilliseconds)
        }
        if (reported != null && reported != expected) {
          seen.add(reported)
        }
      }
      true
    }
    assertThat(found)
      .describedAs("the session never asked the pty for $expected; it asked for $seen")
      .isTrue()
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

  /**
   * Suspends until [condition] holds for [model], checking immediately and then again every
   * [BLOCKS_MODEL_POLL_INTERVAL]. Returns `false` if [timeout] elapses first.
   *
   * This polls on purpose. [org.jetbrains.plugins.terminal.view.shellIntegration.TerminalBlocksModelListener]
   * reports an added, a removed and a replaced block, but never an offset update - so a listener never sees
   * `commandStartOffset`, `outputStartOffset` or `exitCode` change on the active block.
   */
  suspend fun awaitBlocksModelState(
    model: TerminalBlocksModel,
    timeout: Duration = 10.seconds,
    condition: (TerminalBlocksModel) -> Boolean,
  ): Boolean {
    val result = withTimeoutOrNull(timeout) {
      while (!condition(model)) {
        delay(BLOCKS_MODEL_POLL_INTERVAL)
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

  companion object {
    /** How often [awaitBlocksModelState] re-checks its condition. */
    private val BLOCKS_MODEL_POLL_INTERVAL: Duration = 10.milliseconds

    /** How long [awaitReportedSize] waits on the connector queue before it checks the overall timeout. */
    private val REPORTED_SIZE_POLL_INTERVAL: Duration = 10.milliseconds
  }
}

/** Awaits [condition] on [model] via [TerminalViewFixture.awaitOutputModelState] and asserts it was met. */
internal suspend fun TerminalViewFixture.assertOutputModelState(
  model: TerminalOutputModel,
  timeout: Duration = 10.seconds,
  condition: (TerminalOutputModel) -> Boolean,
) {
  assertThat(awaitOutputModelState(model, timeout, condition))
    .describedAs("the output model never satisfied the condition; it holds:\n${model.text}")
    .isTrue()
}

/** Awaits [condition] on [model] via [TerminalViewFixture.awaitBlocksModelState] and asserts it was met. */
internal suspend fun TerminalViewFixture.assertBlocksModelState(
  model: TerminalBlocksModel,
  timeout: Duration = 10.seconds,
  condition: (TerminalBlocksModel) -> Boolean,
) {
  assertThat(awaitBlocksModelState(model, timeout, condition))
    .describedAs("the blocks model never satisfied the condition; it holds:\n${model.blocks.joinToString("\n")}")
    .isTrue()
}
