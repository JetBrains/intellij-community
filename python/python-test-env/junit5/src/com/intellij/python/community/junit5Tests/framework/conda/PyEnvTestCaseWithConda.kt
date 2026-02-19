// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.junit5Tests.framework.conda

import com.intellij.python.junit5Tests.framework.env.PyEnvTestCase
import com.intellij.python.test.env.common.PredefinedPyEnvironments
import com.intellij.python.test.env.junit5.conda.CondaPythonEnvExtension
import org.junit.jupiter.api.extension.ExtendWith

/**
 * Python and conda env test case that supports [com.intellij.python.junit5Tests.framework.env.PythonBinaryPath],
 * [com.intellij.python.junit5Tests.framework.env.PythonSdk], and [CondaEnv] parameter injection.
 * Uses Conda environment from predefined environments.
 * 
 * Example:
 * ```kotlin
@PyEnvTestCaseWithConda
class PyEnvTestExample {
  @Test
  fun test(@CondaEnv conda: PyCondaEnv) { ... }
  
  @Test
  fun testSdk(@PythonSdk sdk: Sdk) { ... }
}
 * ```
 */
@PyEnvTestCase(PredefinedPyEnvironments.CONDA)
@ExtendWith(CondaPythonEnvExtension::class)
annotation class PyEnvTestCaseWithConda