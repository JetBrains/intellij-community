// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.framework.env

/**
 * Mark [com.intellij.openapi.projectRoots.Sdk] test parameter to get Python SDK for env test.
 * If you only need binary path -- use [PythonBinaryPath]
 *
 * Example:
 * ```kotlin
 *   @Test
 *   fun checkPythonSdk(@PythonSdk sdk: Sdk) // SDK is a full Python SDK
 * ```
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
annotation class PythonSdk