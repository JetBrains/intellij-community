// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.framework.env

import com.intellij.python.test.env.junit5.RequiresUvExtension
import org.junit.jupiter.api.extension.ExtendWith

/**
 * Annotation that ensures `uv` tool is available and configured for the test.
 * 
 * This annotation can be applied to test classes or individual test methods.
 * It will automatically configure the Uv path in PropertiesComponent before the test runs.
 * 
 * Usage:
 * ```kotlin
 * // Applied to class - Uv configured for all tests
 * @PyEnvTestCase
 * @RequiresUv
 * class MyUvTest {
 *   @Test
 *   fun testUvFeature() {
 *     // Uv is configured and available here
 *   }
 * }
 * 
 * // Applied to individual test methods
 * @PyEnvTestCase
 * class MyMixedTest {
 *   @Test
 *   @RequiresUv
 *   fun testWithUv() {
 *     // Uv is configured only for this test
 *   }
 *   
 *   @Test
 *   fun testWithoutUv() {
 *     // Uv not configured here
 *   }
 * }
 * ```
 * 
 * Note: This annotation requires @PyEnvTestCase to be present on the class to provide the Python environment.
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@ExtendWith(RequiresUvExtension::class)
annotation class RequiresUv
