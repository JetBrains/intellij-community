// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.tools.sdkTools

import com.intellij.python.sdk.backend.mockSysPathForTests
import com.intellij.python.sdk.backend.mockVersionForTests
import com.intellij.python.sdk.backend.isMockForTests
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.LoadingOrder
import com.intellij.openapi.project.Project
import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.python.sdk.backend.PythonInterpreterDetails
import com.intellij.python.sdk.backend.PythonInterpreterRoots
import com.intellij.python.sdk.backend.PythonInterpreterUpdateRequest
import com.intellij.python.sdk.backend.PythonInterpreterUpdateStep
import com.jetbrains.python.errorProcessing.PyError
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Writes the facts of a mock interpreter of a test from its SDK, see [PythonMockSdkKeys], and stops its update. A mock
 * SDK cannot run, so the probe and the steps after it have nothing to read. The facts belong to the interpreter: all
 * mock SDKs run one binary, so an update of their shared environment would mix their facts.
 *
 * [PythonMockSdk] registers it with the first mock SDK, see [registerOnce]: the test plugins that load this module are
 * not in every test run.
 */
@TestOnly
internal class PythonMockInterpreterFactsStep : PythonInterpreterUpdateStep {
  override val awaited: Boolean get() = true

  override suspend fun update(
    project: Project,
    interpreter: PythonInterpreter,
    request: PythonInterpreterUpdateRequest,
    failure: (PyError) -> Unit,
  ): Boolean {
    if (!interpreter.isMockForTests()) return true
    val details = PythonInterpreterDetails.getInstance(project)
    details.writeVersion(interpreter, interpreter.mockVersionForTests())
    details.writeSysPath(interpreter, interpreter.mockSysPathForTests())
    // A test reads the roots right after the update. The roots follow the facts on their own, so the step waits for them.
    PythonInterpreterRoots.getInstance(project).awaitCommittedForTests()
    return false
  }

  companion object {
    private val registered = AtomicBoolean()

    /** Registers this step for the rest of the test run, after the gate and before the probe, once. */
    @JvmStatic
    fun registerOnce() {
      if (!registered.compareAndSet(false, true)) return
      PythonInterpreterUpdateStep.EP_NAME.point.registerExtension(
        PythonMockInterpreterFactsStep(), LoadingOrder.readOrder("after sdkGate, before probe"), ApplicationManager.getApplication())
    }
  }
}
