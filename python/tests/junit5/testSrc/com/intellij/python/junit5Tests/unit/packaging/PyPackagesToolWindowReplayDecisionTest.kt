// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit.packaging

import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.python.sdk.backend.pythonInterpreterResult
import com.jetbrains.python.packaging.toolwindow.shouldReplayBoundInterpreter
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import com.intellij.testFramework.junit5.TestApplication
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.sdk.PythonSdkType
import com.jetbrains.python.tools.sdkTools.PythonMockSdk
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

/**
 * Covers [shouldReplayBoundInterpreter] — whether a tool window attaching to an already-bound packaging
 * service gets the binding replayed into its fresh panel, or re-binds the service instead.
 *
 * Both mistakes this pins down are real: replaying nothing leaves the header empty on first open,
 * and replaying unconditionally shows a leftover subproject's environment (PY-91300).
 */
@TestApplication
internal class PyPackagesToolWindowReplayDecisionTest {
  @TempDir
  lateinit var root: Path

  private val boundSdk: PythonInterpreter by lazy { interpreterAt("bound") }
  private val otherSdk: PythonInterpreter by lazy { interpreterAt("other") }

  /** An interpreter of its own: its SDK is at its own path, so its ref differs from every other one. */
  private fun interpreterAt(name: String): PythonInterpreter =
    PythonMockSdk.create(name, root.resolve(name).toString(), PythonSdkType.getInstance(), LanguageLevel.getLatest())
      .pythonInterpreterResult().orThrow()

  @Test
  fun `replays when the binding is the interpreter to open on`() {
    assertTrue(shouldReplayBoundInterpreter(boundInterpreter = boundSdk, interpreterToOpenOn = boundSdk),
               "initForInterpreter would short-circuit on the same SDK, leaving the new panel blank")
  }

  @Test
  fun `replays when there is nothing better to open on`() {
    assertTrue(shouldReplayBoundInterpreter(boundInterpreter = boundSdk, interpreterToOpenOn = null),
               "An existing binding beats showing no interpreter at all")
  }

  @Test
  fun `rebinds when the binding belongs to another subproject`() {
    assertFalse(shouldReplayBoundInterpreter(boundInterpreter = boundSdk, interpreterToOpenOn = otherSdk),
                "Replaying a stale binding would open the tool window on a foreign environment")
  }

  @Test
  fun `binds from scratch when the service is not bound yet`() {
    assertFalse(shouldReplayBoundInterpreter(boundInterpreter = null, interpreterToOpenOn = otherSdk),
                "There is no binding to replay")
    assertFalse(shouldReplayBoundInterpreter(boundInterpreter = null, interpreterToOpenOn = null),
                "There is no binding to replay")
  }
}
