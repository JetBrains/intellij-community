// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.junit5Tests.framework.conda

import com.intellij.python.community.junit5Tests.framework.conda.impl.CondaPythonEnvExtension
import com.intellij.python.junit5Tests.framework.env.PyEnvTestCase
import org.junit.jupiter.api.extension.ExtendWith

/**
 * Python and conda env test case that supports [com.intellij.python.junit5Tests.framework.env.PythonBinaryPath] and [CondaEnv]
 * Example:
 * ```kotlin
@PyEnvTestCaseWithConda
class PyEnvTestExample
 * ```
 */
@PyEnvTestCase
@ExtendWith(CondaPythonEnvExtension::class)
annotation class PyEnvTestCaseWithConda