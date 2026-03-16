// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.framework.env

import com.intellij.python.test.env.junit5.RequiresPoetryExtension
import org.junit.jupiter.api.extension.ExtendWith

/**
 * Annotation that ensures Poetry tool is available and configured for the test.
 * 
 * This annotation can be applied to test classes or individual test methods.
 * It will automatically configure the Poetry path in PropertiesComponent before the test runs.
 * 
 * Usage:
 * ```kotlin
 * // Applied to class - Poetry configured for all tests
 * @PyEnvTestCase
 * @RequiresPoetry
 * class MyPoetryTest {
 *   @Test
 *   fun testPoetryFeature() {
 *     // Poetry is configured and available here
 *   }
 * }
 * 
 * // Applied to individual test methods
 * @PyEnvTestCase
 * class MyMixedTest {
 *   @Test
 *   @RequiresPoetry
 *   fun testWithPoetry() {
 *     // Poetry is configured only for this test
 *   }
 *   
 *   @Test
 *   fun testWithoutPoetry() {
 *     // Poetry not configured here
 *   }
 * }
 * ```
 * 
 * Note: This annotation requires @PyEnvTestCase to be present on the class to provide the Python environment.
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@ExtendWith(RequiresPoetryExtension::class)
annotation class RequiresPoetry
