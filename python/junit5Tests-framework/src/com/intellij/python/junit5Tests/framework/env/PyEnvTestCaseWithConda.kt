// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.framework.env

import com.intellij.python.junit5Tests.framework.env.impl.CondaPythonEnvExtension
import org.junit.jupiter.api.extension.ExtendWith

/**
 * Python and conda env test case that supports [PythonBinaryPath] and [CondaEnv]
 * Example:
 * ```kotlin
@PyEnvTestCaseWithConda
class PyEnvTestExample
 * ```
 */
@PyEnvTestCase
@ExtendWith(CondaPythonEnvExtension::class)
annotation class PyEnvTestCaseWithConda