// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.framework.env

/**
 * Mark [java.nio.file.Path] test parameter to get first python binary for env test.
 * If you need sdk -- use [pySdkFixture]
 *
 * Example:
 * ```kotlin
 *   @Test
 *   fun checkPythonPath(@PythonSdkPath path: Path) // Path is python.exe for example
 * ```
 */

@Target(AnnotationTarget.VALUE_PARAMETER)
annotation class PythonBinaryPath
