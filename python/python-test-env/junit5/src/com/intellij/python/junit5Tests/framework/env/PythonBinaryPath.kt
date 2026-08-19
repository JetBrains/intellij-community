// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.framework.env

import com.intellij.python.community.junit5Tests.framework.conda.PyEnvTestCaseWithConda

/**
 * Mark [java.nio.file.Path] test parameter to get first python binary for env test.
 *
 * Example:
 * ```kotlin
 *   @Test
 *   fun checkPythonPath(@PythonSdkPath path: PythonBinary) // Path is python.exe for example
 * ```
 *
 * When used with [PyEnvTestCaseWithConda], ponts to conda base path.
 */

@Target(AnnotationTarget.VALUE_PARAMETER)
annotation class PythonBinaryPath
