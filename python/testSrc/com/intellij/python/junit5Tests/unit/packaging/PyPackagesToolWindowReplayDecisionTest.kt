// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit.packaging

import com.intellij.openapi.projectRoots.Sdk
import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.python.sdk.backend.pythonInterpreter
import com.jetbrains.python.packaging.toolwindow.shouldReplayBoundInterpreter
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock

/**
 * Covers [shouldReplayBoundInterpreter] — whether a tool window attaching to an already-bound packaging
 * service gets the binding replayed into its fresh panel, or re-binds the service instead.
 *
 * Both mistakes this pins down are real: replaying nothing leaves the header empty on first open,
 * and replaying unconditionally shows a leftover subproject's environment (PY-91300).
 */
internal class PyPackagesToolWindowReplayDecisionTest {
  private val boundSdk: PythonInterpreter = mock(Sdk::class.java).pythonInterpreter()
  private val otherSdk: PythonInterpreter = mock(Sdk::class.java).pythonInterpreter()

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
