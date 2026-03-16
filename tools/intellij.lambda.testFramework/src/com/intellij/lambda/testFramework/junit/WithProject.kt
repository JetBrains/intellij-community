package com.intellij.lambda.testFramework.junit

import com.intellij.ide.starter.project.ProjectInfoSpec
import kotlin.reflect.KClass

/**
 * Annotation to specify which project should be used for a test.
 *
 * The project will be prepared as a fresh copy before each test using the specified [ProjectInfoSpec] implementation.
 *
 * Can be applied at class level (default for all methods) or method level (overrides class-level).
 *
 * Example:
 * ```kotlin
 * object TestAppProject : ProjectInfoSpec by ReusableLocalProjectInfo(
 *   projectDir = JarUtils.extractResource("projects/TestApp", Files.createTempDirectory("ui-test-resource-")))
 *
 * @WithProject(TestAppProject::class)  // applies to all methods
 * class MyTest {
 *   @Test
 *   fun testA() { /* uses TestAppProject */ }
 * }
 * ```
 *
 * @param project the [ProjectInfoSpec] class that defines how to prepare the project
 * @see com.intellij.ide.starter.project.ProjectInfoSpec
 * @see com.intellij.ide.starter.project.RemoteArchiveProjectInfo
 * @see com.intellij.ide.starter.project.ReusableLocalProjectInfo
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class WithProject(
  val project: KClass<out ProjectInfoSpec>,
)
