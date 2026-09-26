package com.jetbrains.python.fixtures

/**
 * Opts a [PyTestCase]-based test (a single test method or a whole test class, inherited by subclasses)
 * OUT of the `python.type.any` engine, i.e. into the legacy type behaviour where `Any`/`Unknown` support
 * is disabled.
 *
 * By default every [PyTestCase] test runs with `python.type.any` enabled (see `PyTestCase.setUp`).
 * Annotate the test method or test class with this to restore the old behaviour for that test only.
 *
 * For [PyCodeInsightTestCase]-based tests, opt out via `TestOptions(enablePyAnyType = false)` instead.
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class PyAnyTypeDisabled
