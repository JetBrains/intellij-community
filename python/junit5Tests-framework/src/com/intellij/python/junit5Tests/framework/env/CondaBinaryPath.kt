// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.framework.env

/**
 * Mark [com.jetbrains.python.sdk.flavors.conda.PyCondaEnv] test parameter to get first conda binary for env test.
 * If you need sdk -- use [pySdkFixture]
 *
 * Example:
 * ```kotlin
 *   @Test
 *   fun checkConda(@CondaEnv conda: PyCondaEnv) // Conda base env
 * ```
 */

@Target(AnnotationTarget.VALUE_PARAMETER)
annotation class CondaEnv
