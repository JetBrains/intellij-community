// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.tests.reworked.frontend.session

import com.intellij.platform.util.coroutines.childScope
import com.intellij.terminal.tests.reworked.util.TerminalSessionTestUtil
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.common.timeoutRunBlocking
import kotlinx.coroutines.cancel
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.terminal.TerminalEmulatorType
import org.junit.Rule
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

/**
 * How `createTerminalSession` resolves the [TerminalEmulatorType] of a session: an emulator specified in
 * [org.jetbrains.plugins.terminal.ShellStartupOptions.emulatorType] always wins, and only an unspecified
 * (null) emulator falls back to [TerminalEmulatorType.default], i.e. the registry key behind it.
 *
 * The chosen implementation is asserted by its runtime class name — the session classes are internal to the
 * frontend module, so they cannot be referenced statically from here, and the emulators are deliberately
 * indistinguishable through the session API itself (even 256-color SGR, where the emulators differ, is
 * normalized to RGB by both session implementations). Each emulator's actual pipeline behavior is covered by
 * the parameterized suites (see [TerminalSessionTestCase]); this test only pins which pipeline gets chosen.
 */
internal class TerminalEmulatorTypeResolutionTest {
  private val projectRule = ProjectRule()
  private val disposableRule = DisposableRule()

  @Rule
  @JvmField
  val ruleChain: RuleChain = RuleChain(projectRule, disposableRule)

  @Test
  fun `explicit Ghostty wins when the registry says JediTerm`() {
    TerminalEmulatorType.JediTerm.setDefault(disposableRule.disposable)
    assertSessionUsesEmulator(requested = TerminalEmulatorType.Ghostty, expected = TerminalEmulatorType.Ghostty)
  }

  @Test
  fun `explicit JediTerm wins when the registry says Ghostty`() {
    TerminalEmulatorType.Ghostty.setDefault(disposableRule.disposable)
    assertSessionUsesEmulator(requested = TerminalEmulatorType.JediTerm, expected = TerminalEmulatorType.JediTerm)
  }

  @Test
  fun `unspecified emulator falls back to the registry saying Ghostty`() {
    TerminalEmulatorType.Ghostty.setDefault(disposableRule.disposable)
    assertSessionUsesEmulator(requested = null, expected = TerminalEmulatorType.Ghostty)
  }

  @Test
  fun `unspecified emulator falls back to the registry saying JediTerm`() {
    TerminalEmulatorType.JediTerm.setDefault(disposableRule.disposable)
    assertSessionUsesEmulator(requested = null, expected = TerminalEmulatorType.JediTerm)
  }

  /** Starts a loopback session with [requested] in its startup options and asserts the [expected] emulator is behind it. */
  private fun assertSessionUsesEmulator(requested: TerminalEmulatorType?, expected: TerminalEmulatorType) {
    timeoutRunBlocking(20.seconds) {
      val sessionScope = childScope("TerminalSession")
      try {
        val (session, _) =
          TerminalSessionTestUtil.createLoopbackTerminalSession(projectRule.project, sessionScope, emulatorType = requested)
        val expectedClassName = when (expected) {
          TerminalEmulatorType.Ghostty -> "GhosttyTerminalSession"
          TerminalEmulatorType.JediTerm -> "TerminalSessionImpl"
        }
        assertThat(session.javaClass.simpleName).isEqualTo(expectedClassName)
      }
      finally {
        sessionScope.cancel()
      }
    }
  }
}
