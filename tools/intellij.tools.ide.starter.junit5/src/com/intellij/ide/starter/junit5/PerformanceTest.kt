package com.intellij.ide.starter.junit5

import java.lang.annotation.Inherited

/**
 * Marks a test class or a test method as a performance test: a test that measures how fast the IDE is or how many resources it uses,
 * rather than whether it behaves correctly.
 *
 * The marker only describes the test; each part of the test framework decides what to do with it. Currently:
 * - [TestScreenRecorderExtension] does not record the screen during a performance test, because a recorder running next to the IDE
 *   adds load to the machine and would distort the measurements.
 *
 * On a class, the marker covers every test method of the class, of its subclasses, and of its `@Nested` classes.
 *
 * Unlike `@PerformanceUnitTest`, this marker does not change which tests run on CI.
 */
@Inherited
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class PerformanceTest