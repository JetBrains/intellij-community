// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.junit5.env

/**
 * Mark [java.nio.file.Path] test parameter to get first python binary for env test
 *
 * Example:
 * ```kotlin
 *   @Test
 *   fun checkPythonPath(@PythonSdkPath path: Path) // Path is python.exe for example
 * ```
 */

@Target(AnnotationTarget.VALUE_PARAMETER)
internal annotation class PythonBinaryPath
