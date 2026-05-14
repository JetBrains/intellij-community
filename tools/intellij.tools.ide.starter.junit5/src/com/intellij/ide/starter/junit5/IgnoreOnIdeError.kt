// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.starter.junit5

import org.junit.jupiter.api.extension.ExtendWith

/**
 * Marks a JUnit5 test (or all tests in a class) as eligible to be reported as IGNORED
 * (instead of FAILED) when a matching IDE-side error has been captured during the test run.
 *
 * The extension only acts when the test would otherwise fail. A passing test is left untouched
 * even if a matching IDE error was captured.
 *
 * Can be applied on test class and test method level.
 *
 * At least one of [messageRegex] or [stacktraceRegex] must be non-empty. When both are set, both must
 * match (AND). Multiple annotations on the same method or class are OR-ed.
 */
@ExtendWith(IgnoreOnIdeErrorExtension::class)
@JvmRepeatable(IgnoreOnIdeErrors::class)
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
annotation class IgnoreOnIdeError(
  /** Regex matched (via `containsMatchIn`) against the IDE error's message text. */
  val messageRegex: String = "",
  /** Regex matched (via `containsMatchIn`) against the IDE error's stack trace text. */
  val stacktraceRegex: String = "",
  /** Human-readable reason surfaced in the skip message reported to TeamCity. */
  val reason: String = "",
)

/** Container for repeated [IgnoreOnIdeError]; not for direct use. */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
internal annotation class IgnoreOnIdeErrors(val value: Array<IgnoreOnIdeError>)
