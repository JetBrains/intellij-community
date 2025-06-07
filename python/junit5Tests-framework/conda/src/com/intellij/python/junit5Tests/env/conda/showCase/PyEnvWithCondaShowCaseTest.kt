// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.env.conda.showCase

import com.intellij.python.community.junit5Tests.framework.conda.CondaEnv
import com.intellij.python.community.junit5Tests.framework.conda.PyEnvTestCaseWithConda
import com.intellij.python.junit5Tests.framework.env.PyEnvTestCase
import com.intellij.python.junit5Tests.framework.env.PythonBinaryPath
import com.jetbrains.python.sdk.flavors.conda.PyCondaEnv
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isExecutable
import kotlin.io.path.isRegularFile

/**
 * Ensures [com.intellij.python.junit5Tests.framework.env.PythonBinaryPath] works.
 */
@PyEnvTestCaseWithConda
@PyEnvTestCase
class PyEnvWithCondaShowCaseTest {
  @Test
  fun checkPythonPath(@PythonBinaryPath python: Path) {
    ensurePythonWorks(python)
  }

  @Test
  fun checkCondaPath(@CondaEnv condaEnv: PyCondaEnv) {
    ensurePythonWorks(Path.of(condaEnv.fullCondaPathOnTarget))
  }

  private fun ensurePythonWorks(python: Path) {
    Assertions.assertTrue(python.exists(), "$python doesn't exist")
    Assertions.assertTrue(python.isRegularFile(), "$python isn't file")
    Assertions.assertTrue(python.isExecutable(), "$python isn't executable")
  }
}