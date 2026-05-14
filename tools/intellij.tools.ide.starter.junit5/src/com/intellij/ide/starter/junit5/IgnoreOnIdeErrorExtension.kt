// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.starter.junit5

import com.intellij.ide.starter.report.Error
import com.intellij.ide.starter.report.ErrorReporterToCI
import com.intellij.ide.starter.report.ErrorType
import com.intellij.ide.starter.runner.IDERunContext
import com.intellij.ide.starter.runner.events.IdeAfterLaunchEvent
import com.intellij.tools.ide.starter.bus.EventsBus
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.TestExecutionExceptionHandler
import org.opentest4j.TestAbortedException
import java.util.concurrent.CopyOnWriteArrayList

/**
 * JUnit5 extension that downgrades a failing test to ABORTED (reported to TeamCity as `testIgnored`)
 * when the IDE under test produced an error matching an [IgnoreOnIdeError] filter.
 *
 * **Scope:**
 *  - Method-level filters use per-test state: only errors from that test are checked.
 *  - Class-level filters use shared state: errors from any test in the class are accumulated and checked.
 *
 * Lifecycle:
 *  - `beforeEach`: subscribes to [IdeAfterLaunchEvent]. Creates per-test state for method-level filters,
 *    and retrieves/creates shared class-level state for class-level filters.
 *  - `handleTestExecutionException`: on test failure, checks:
 *    1. Method-level filters against per-test run contexts (if any method-level annotations exist)
 *    2. Class-level filters against accumulated class-level run contexts (if any class-level annotations exist)
 *    If any filter matches, throws [TestAbortedException] (cause: the original failure).
 *    Otherwise rethrows the original failure unchanged.
 *  - `afterEach`: unsubscribes per-test state and removes it. Class-level state persists across all tests.
 *
 * A passing test is never downgraded: this handler is only invoked when the test threw.
 */
internal class IgnoreOnIdeErrorExtension : BeforeEachCallback, AfterEachCallback, TestExecutionExceptionHandler {

  override fun beforeEach(context: ExtensionContext) {
    val store = context.getStore(NAMESPACE)

    // Per-test state for method-level annotations
    val testState = State()
    store.put(TEST_STATE_KEY, testState)
    EventsBus.subscribe<IdeAfterLaunchEvent>(testState) { event ->
      testState.runContexts.add(event.runContext)
    }

    // Get or create class-level shared state for class-level annotations
    val classLevelFilters = collectClassLevelFilters(context)
    if (classLevelFilters.isNotEmpty()) {
      val classState = store.get(CLASS_STATE_KEY, State::class.java)
      if (classState == null) {
        // First test in the class: create class state and subscribe it
        val newClassState = State()
        store.put(CLASS_STATE_KEY, newClassState)
        EventsBus.subscribe<IdeAfterLaunchEvent>(newClassState) { event ->
          newClassState.runContexts.add(event.runContext)
        }
      }
      // Subsequent tests: state already exists and subscribed; nothing to do
    }
  }

  override fun afterEach(context: ExtensionContext) {
    val store = context.getStore(NAMESPACE)
    val testState = store.remove(TEST_STATE_KEY, State::class.java) ?: return
    EventsBus.unsubscribe<IdeAfterLaunchEvent>(testState)
    // Note: class state persists across all tests and is cleaned up when the class context ends
  }

  override fun handleTestExecutionException(context: ExtensionContext, throwable: Throwable) {
    val methodFilters = collectMethodLevelFilters(context)
    val testState = context.getStore(NAMESPACE).get(TEST_STATE_KEY, State::class.java)
    if (methodFilters.isNotEmpty()) {
      checkFiltersAndThrow(methodFilters, testState?.collectErrors().orEmpty(), throwable)
    }

    val classFilters = collectClassLevelFilters(context)
    if (classFilters.isNotEmpty()) {
      val classState = context.getStore(NAMESPACE).get(CLASS_STATE_KEY, State::class.java)
      checkFiltersAndThrow(classFilters, classState?.collectErrors().orEmpty(), throwable)
    }

    throw throwable
  }

  private fun checkFiltersAndThrow(filters: List<IgnoreOnIdeError>, errors: List<Error>, throwable: Throwable) {
    for (filter in filters) {
      val match = errors.firstOrNull { matches(filter, it) } ?: continue
      val reason = filter.reason.ifEmpty { "matched filter ${describe(filter)}" }
      throw TestAbortedException(
        "Test ignored due to IDE error: $reason. Matched: ${firstLine(match.messageText)}",
        throwable,
      )
    }
  }

  private fun collectMethodLevelFilters(context: ExtensionContext): List<IgnoreOnIdeError> {
    val method: java.lang.reflect.Method? = context.testMethod.orElse(null)
    return if (method != null) {
      method.getAnnotationsByType(IgnoreOnIdeError::class.java).toList()
    } else {
      emptyList()
    }
  }

  private fun collectClassLevelFilters(context: ExtensionContext): List<IgnoreOnIdeError> {
    val result = mutableListOf<IgnoreOnIdeError>()
    var c: Class<*>? = context.testClass.orElse(null)
    while (c != null && c != Any::class.java) {
      result.addAll(c.getAnnotationsByType(IgnoreOnIdeError::class.java))
      c = c.superclass
    }
    return result
  }

  private fun matches(filter: IgnoreOnIdeError, error: Error): Boolean {
    // Freezes have no message of their own — keep the comparison strictly to actual exceptions for v1.
    if (error.type != ErrorType.ERROR) return false
    if (filter.messageRegex.isEmpty() && filter.stacktraceRegex.isEmpty()) return false
    if (filter.messageRegex.isNotEmpty() && !Regex(filter.messageRegex).containsMatchIn(error.messageText)) return false
    if (filter.stacktraceRegex.isNotEmpty() && !Regex(filter.stacktraceRegex).containsMatchIn(error.stackTraceContent)) return false
    return true
  }

  private fun describe(filter: IgnoreOnIdeError): String {
    val parts = buildList {
      if (filter.messageRegex.isNotEmpty()) add("messageRegex=\"${filter.messageRegex}\"")
      if (filter.stacktraceRegex.isNotEmpty()) add("stacktraceRegex=\"${filter.stacktraceRegex}\"")
    }
    return parts.joinToString(", ")
  }

  private fun firstLine(text: String): String =
    text.lineSequence().firstOrNull()?.take(MAX_FIRST_LINE_LEN).orEmpty()

  private class State {
    val runContexts: MutableList<IDERunContext> = CopyOnWriteArrayList()

    fun collectErrors(): List<Error> =
      runContexts.flatMap { ErrorReporterToCI.collectErrors(it.logsDir) }
  }

  companion object {
    private val NAMESPACE: ExtensionContext.Namespace =
      ExtensionContext.Namespace.create(IgnoreOnIdeErrorExtension::class.java)
    private const val TEST_STATE_KEY = "test_state"
    private const val CLASS_STATE_KEY = "class_state"
    private const val MAX_FIRST_LINE_LEN = 200
  }
}
