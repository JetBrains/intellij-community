package com.intellij.ide.starter.junit5

import java.lang.annotation.Inherited
import java.lang.reflect.Method

/**
 * Marks a performance test class or method.
 *
 * [TestScreenRecorderExtension] doesn't record the screen for such tests, so recording doesn't distort the measurements.
 *
 * On a local run, a test that publishes metrics through `MetricsPublisher` without this annotation gets an extra failed
 * TestInfraException test.
 */
@Inherited
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class PerformanceTest

/**
 * Tells if [PerformanceTest] marks [testMethod] or [testClass]. A class also takes the annotation of its enclosing
 * class, and, because the annotation is [Inherited], of its superclass.
 */
fun isPerformanceTest(testClass: Class<*>?, testMethod: Method?): Boolean =
  testMethod?.isAnnotationPresent(PerformanceTest::class.java) == true ||
  generateSequence(testClass) { it.enclosingClass }.any { it.isAnnotationPresent(PerformanceTest::class.java) }