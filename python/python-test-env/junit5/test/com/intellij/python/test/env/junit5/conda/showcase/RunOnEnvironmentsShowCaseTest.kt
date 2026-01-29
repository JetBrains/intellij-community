// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.test.env.junit5.conda.showcase

import com.intellij.python.venv.createVenv
import com.intellij.python.junit5Tests.framework.env.BeforeRunOnEnvironmentInvocation
import com.intellij.python.junit5Tests.framework.env.PyEnvTestCase
import com.intellij.python.junit5Tests.framework.env.PythonBinaryPath
import com.intellij.python.junit5Tests.framework.env.RunOnEnvironments
import com.intellij.python.test.env.common.PredefinedPyEnvironments
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isExecutable
import kotlin.io.path.isRegularFile

@PyEnvTestCase
@RunOnEnvironments(PredefinedPyEnvironments.VANILLA_3_13, PredefinedPyEnvironments.VANILLA_3_14)
class RunOnEnvironmentsShowCaseTest {

  @BeforeRunOnEnvironmentInvocation
  fun init(@PythonBinaryPath python: Path, @TempDir tempDir: Path) {
    runBlocking {
      createVenv(python, tempDir)
    }
  }

  @Test
  fun checkPythonPath(@PythonBinaryPath python: Path) {
    ensurePythonWorks(python)
  }

  private fun ensurePythonWorks(python: Path) {
    Assertions.assertTrue(python.exists(), "$python doesn't exist")
    Assertions.assertTrue(python.isRegularFile(), "$python isn't file")
    Assertions.assertTrue(python.isExecutable(), "$python isn't executable")
  }
}