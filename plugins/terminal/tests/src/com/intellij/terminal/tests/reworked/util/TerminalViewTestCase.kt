// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.tests.reworked.util

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.projectFixture
import kotlinx.coroutines.Dispatchers
import org.jetbrains.plugins.terminal.TerminalEmulatorType
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.EnumSource
import kotlin.time.Duration.Companion.seconds

/**
 * Base class for tests that drive a real [com.intellij.terminal.frontend.view.impl.TerminalViewImpl] connected to
 * a loopback-backed session - no real shell process.
 *
 * [ParameterizedClass] runs the whole class once per [TerminalEmulatorType]:
 * 1. [TerminalEmulatorType.Ghostty]
 * 2. [TerminalEmulatorType.JediTerm]
 */
@TestApplication
@Timeout(30)
@ParameterizedClass
@EnumSource(TerminalEmulatorType::class)
internal abstract class TerminalViewTestCase(protected val emulatorType: TerminalEmulatorType) {

  protected val project get() = projectFixture.get()

  @TestDisposable
  protected lateinit var disposable: Disposable

  /**
   * Connects a [TerminalViewFixture] for [emulatorType], runs [test], and closes the fixture afterwards to stop
   * the emulation.
   */
  protected fun doTest(test: suspend (fixture: TerminalViewFixture) -> Unit) {
    timeoutRunBlocking(30.seconds, context = Dispatchers.EDT) {
      TerminalViewFixture(project, emulatorType).use { fixture -> test(fixture) }
    }
  }

  protected fun assumeJediTerm() {
    Assumptions.assumeTrue(emulatorType == TerminalEmulatorType.JediTerm, "Not applicable to the Ghostty emulator")
  }

  protected fun assumeGhostty() {
    Assumptions.assumeTrue(emulatorType == TerminalEmulatorType.Ghostty, "Not applicable to the JediTerm emulator")
  }

  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }
}
