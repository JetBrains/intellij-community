// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit

import com.intellij.platform.eel.provider.utils.EelProcessExecutionResult
import com.intellij.python.community.execService.python.ZeroCodeVersionStringTransformer
import com.jetbrains.python.Result
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ZeroCodeVersionStringTransformerTest {
  @Test
  fun `the version of Python 3 comes from stdout`() {
    assertEquals(Result.Success("Python 3.12.4"), transform(stdout = "Python 3.12.4\n"))
  }

  @Test
  fun `the version of Python 2 comes from stderr`() {
    assertEquals(Result.Success("Python 2.7.18"), transform(stderr = "Python 2.7.18\n"))
  }

  @Test
  fun `an output without a version is a failure`() {
    assertTrue(transform(stdout = "nothing", stderr = "warning") is Result.Failure)
  }

  @Test
  fun `a non-zero exit code is a failure`() {
    assertTrue(transform(exitCode = 1, stderr = "Python 2.7.18\n") is Result.Failure)
  }

  private fun transform(exitCode: Int = 0, stdout: String = "", stderr: String = ""): Result<String, String?> =
    ZeroCodeVersionStringTransformer(EelProcessExecutionResult(exitCode, stdout.toByteArray(), stderr.toByteArray()))
}
