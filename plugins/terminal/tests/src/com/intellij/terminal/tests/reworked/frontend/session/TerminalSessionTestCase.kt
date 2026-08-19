// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.tests.reworked.frontend.session

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.util.coroutines.childScope
import com.intellij.terminal.tests.reworked.util.LoopbackTtyConnector
import com.intellij.terminal.tests.reworked.util.TerminalOutputEventCollector
import com.intellij.terminal.tests.reworked.util.TerminalSessionTestUtil
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.common.timeoutRunBlocking
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import org.jetbrains.plugins.terminal.TerminalEmulatorType
import org.jetbrains.plugins.terminal.session.impl.TerminalSession
import org.junit.Rule
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import kotlin.time.Duration.Companion.seconds

/**
 * Base class for tests that must hold for **both** VT emulators: every case runs once per
 * [TerminalEmulatorType].
 *
 * Emulator-specific behavior does not belong here — put it in a fixture pinned to one backend, such as
 * `ghostty.GhosttyTerminalSessionTestCase`. A case that genuinely cannot apply to one emulator can opt out with
 * `Assume`, but that is a smell worth a comment.
 */
@RunWith(Parameterized::class)
internal abstract class TerminalSessionTestCase(protected val emulatorType: TerminalEmulatorType) {
  private val projectRule: ProjectRule = ProjectRule()
  private val disposableRule = DisposableRule()

  @Rule
  @JvmField
  val ruleChain: RuleChain = RuleChain(projectRule, disposableRule)

  /** Disposed at the end of each test; for registering per-test cleanup. */
  protected val disposable: Disposable get() = disposableRule.disposable

  /**
   * Starts a loopback-backed session for the current [emulatorType] with a persistent output collector, runs [test],
   * and cancels the session scope afterwards to stop the emulation.
   *
   * The collector subscribes once for the whole test, so [test] can await events without losing earlier ones.
   * Unlike `GhosttyTerminalSessionTestCase`, the session scope inherits [timeoutRunBlocking]'s dispatcher: no
   * case here parks the test thread, so there is nothing to starve.
   */
  protected fun runSessionTest(
    test: suspend (session: TerminalSession, connector: LoopbackTtyConnector, collector: TerminalOutputEventCollector) -> Unit,
  ) = runSessionTestWithoutCollector { session, connector, sessionScope ->
    test(session, connector, TerminalOutputEventCollector(session, sessionScope))
  }

  /**
   * Like [runSessionTest], but subscribing is left to [test]: the session starts with nothing collecting its
   * output, so a case can assert what becomes of output produced before anyone is listening.
   */
  protected fun runSessionTestWithoutCollector(
    test: suspend (session: TerminalSession, connector: LoopbackTtyConnector, sessionScope: CoroutineScope) -> Unit,
  ) {
    emulatorType.setDefault(disposableRule.disposable)
    timeoutRunBlocking(20.seconds) {
      val sessionScope = childScope("TerminalSession")
      try {
        val (session, connector) = TerminalSessionTestUtil.createLoopbackTerminalSession(projectRule.project, sessionScope)
        test(session, connector, sessionScope)
      }
      finally {
        sessionScope.cancel()
      }
    }
  }

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun emulatorTypes(): List<TerminalEmulatorType> =
      listOf(TerminalEmulatorType.JediTerm, TerminalEmulatorType.Ghostty)
  }
}

/**
 * Makes this emulator [TerminalEmulatorType.default] until [disposable] is disposed.
 */
internal fun TerminalEmulatorType.setDefault(disposable: Disposable) {
  Registry.get("terminal.use.ghostty.emulator").setValue(this == TerminalEmulatorType.Ghostty, disposable)
}
