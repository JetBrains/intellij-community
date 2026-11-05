package com.intellij.ide.starter.junit5

import java.lang.annotation.Inherited

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