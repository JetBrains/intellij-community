// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.tests.reworked.frontend.session.ghostty

import com.intellij.platform.util.coroutines.childScope
import com.intellij.terminal.tests.reworked.frontend.session.setDefault
import com.intellij.terminal.tests.reworked.util.LoopbackTtyConnector
import com.intellij.terminal.tests.reworked.util.TerminalOutputEventCollector
import com.intellij.terminal.tests.reworked.util.TerminalSessionTestUtil
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.common.timeoutRunBlocking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import org.jetbrains.plugins.terminal.TerminalEmulatorType
import org.jetbrains.plugins.terminal.session.impl.TerminalSession
import org.junit.Rule
import kotlin.time.Duration.Companion.seconds

/**
 * Base class for tests of the Ghostty-backed
 * [com.intellij.terminal.frontend.session.ghostty.GhosttyTerminalSession].
 *
 * Subclasses get a loopback-driven session per test — no shell process — and assert on the
 * [org.jetbrains.plugins.terminal.session.impl.TerminalOutputEvent]s it produces. Tests that must run on
 * *both* emulators belong in the parent package instead, parameterized over the VT emulator (see
 * `TerminalSessionOutputEventsTest`); everything here is specific to this backend.
 */
internal abstract class GhosttyTerminalSessionTestCase {
  private val projectRule = ProjectRule()
  private val disposableRule = DisposableRule()

  @Rule
  @JvmField
  val ruleChain: RuleChain = RuleChain(projectRule, disposableRule)

  /**
   * Starts a loopback-backed Ghostty session with a persistent output collector, runs [test], and cancels the
   * session scope afterward to stop the emulation.
   *
   * The collector subscribes once for the whole test, so [test] can await events without losing earlier ones.
   * The session scope deliberately runs on [Dispatchers.Default] rather than the dispatcher inherited from
   * [timeoutRunBlocking]: a test that parks the test thread (on a latch, say) would otherwise starve the
   * session's own coroutines, including its synchronized-output watchdog.
   */
  protected fun runSessionTest(
    test: suspend (session: TerminalSession, connector: LoopbackTtyConnector, collector: TerminalOutputEventCollector) -> Unit,
  ) {
    TerminalEmulatorType.Ghostty.setDefault(disposableRule.disposable)
    timeoutRunBlocking(30.seconds) {
      val sessionScope = childScope("TerminalSession", Dispatchers.Default)
      try {
        val (session, connector) = TerminalSessionTestUtil.createLoopbackTerminalSession(projectRule.project, sessionScope)
        val collector = TerminalOutputEventCollector(session, sessionScope)
        test(session, connector, collector)
      }
      finally {
        sessionScope.cancel()
      }
    }
  }
}

/** Wraps [body] in a CSI (Control Sequence Introducer, `ESC [`) sequence. */
internal fun csi(body: String): String = Char(27) + "[" + body

/** BEL (0x07), which the session reports as a `TerminalBeepEvent`. */
internal val BELL: String = Char(7).toString()

/** How long to wait for something that is expected to arrive before failing the test. */
internal const val AWAIT_TIMEOUT_MS: Long = 10_000
