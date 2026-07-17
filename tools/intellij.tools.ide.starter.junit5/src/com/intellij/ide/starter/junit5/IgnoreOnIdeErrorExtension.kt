// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.starter.junit5

import com.intellij.ide.starter.report.Error
import com.intellij.ide.starter.report.ErrorReporterToCI
import com.intellij.ide.starter.report.ErrorType
import com.intellij.ide.starter.runner.IDERunContext
import com.intellij.ide.starter.runner.events.IdeAfterLaunchEvent
import com.intellij.tools.ide.starter.bus.EventsBus
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.LifecycleMethodExecutionExceptionHandler
import org.junit.jupiter.api.extension.TestExecutionExceptionHandler
import org.opentest4j.TestAbortedException
import java.util.concurrent.CopyOnWriteArrayList

/**
 * JUnit5 extension that downgrades a failing test to ABORTED (reported to TeamCity as `testIgnored`)
 * when the IDE under test produced an error matching an [IgnoreOnIdeError] filter.
 *
 * The failure is downgraded no matter where it is thrown: from the test body, or from a lifecycle
 * method (`@BeforeAll`/`@BeforeEach`/`@AfterEach`/`@AfterAll`). This matters because IDE runs often
 * happen during setup (e.g. project import / library resolution): a flaky IDE error there fails the
 * test as a "Class Configuration" error, which must be ignored just like a matching test-body failure.
 *
 * **Scope:**
 *  - Method-level filters use per-test state: only errors from that test are checked.
 *  - Class-level filters use shared state: errors from any run in the class (including class-level
 *    setup in `@BeforeAll`) are accumulated and checked.
 *
 * Lifecycle:
 *  - `beforeAll`: for class-level filters, creates shared class-level state and subscribes it to
 *    [IdeAfterLaunchEvent] *before* any user `@BeforeAll` runs, so IDE errors produced during
 *    class-level setup are captured.
 *  - `beforeEach`: subscribes per-test state to [IdeAfterLaunchEvent] for method-level filters.
 *  - `handleTestExecutionException` / `handle*MethodExecutionException`: on failure, checks:
 *    1. Method-level filters against per-test run contexts (for method scope; skipped for `@BeforeAll`/`@AfterAll`)
 *    2. Class-level filters against accumulated class-level run contexts (if any class-level annotations exist)
 *    If any filter matches, throws [TestAbortedException] (cause: the original failure).
 *    Otherwise rethrows the original failure unchanged.
 *  - `afterEach`: unsubscribes per-test state and removes it.
 *  - `afterAll`: unsubscribes class-level state and removes it.
 *
 * A passing test is never downgraded: these handlers are only invoked when something threw.
 */
internal class IgnoreOnIdeErrorExtension :
  BeforeAllCallback,
  AfterAllCallback,
  BeforeEachCallback,
  AfterEachCallback,
  TestExecutionExceptionHandler,
  LifecycleMethodExecutionExceptionHandler {

  override fun beforeAll(context: ExtensionContext) {
    // Class-level state must be subscribed before any user `@BeforeAll` runs so that IDE errors
    // produced during class-level setup (project import, library resolution, indexing) are captured.
    if (collectClassLevelFilters(context).isEmpty()) return
    val store = context.getStore(NAMESPACE)
    if (store.get(CLASS_STATE_KEY, State::class.java) != null) return
    val classState = State()
    store.put(CLASS_STATE_KEY, classState)
    EventsBus.subscribe<IdeAfterLaunchEvent>(classState) { event ->
      classState.runContexts.add(event.runContext)
    }
  }

  override fun afterAll(context: ExtensionContext) {
    val store = context.getStore(NAMESPACE)
    val classState = store.remove(CLASS_STATE_KEY, State::class.java) ?: return
    EventsBus.unsubscribe<IdeAfterLaunchEvent>(classState)
  }

  override fun beforeEach(context: ExtensionContext) {
    val store = context.getStore(NAMESPACE)

    // Per-test state for method-level annotations
    val testState = State()
    store.put(TEST_STATE_KEY, testState)
    EventsBus.subscribe<IdeAfterLaunchEvent>(testState) { event ->
      testState.runContexts.add(event.runContext)
    }
  }

  override fun afterEach(context: ExtensionContext) {
    val store = context.getStore(NAMESPACE)
    val testState = store.remove(TEST_STATE_KEY, State::class.java) ?: return
    EventsBus.unsubscribe<IdeAfterLaunchEvent>(testState)
    // Note: class state persists across all tests and is cleaned up in `afterAll`.
  }

  override fun handleTestExecutionException(context: ExtensionContext, throwable: Throwable) {
    abortIfIdeErrorMatches(context, throwable, includeMethodLevel = true)
    throw throwable
  }

  override fun handleBeforeEachMethodExecutionException(context: ExtensionContext, throwable: Throwable) {
    abortIfIdeErrorMatches(context, throwable, includeMethodLevel = true)
    throw throwable
  }

  override fun handleAfterEachMethodExecutionException(context: ExtensionContext, throwable: Throwable) {
    abortIfIdeErrorMatches(context, throwable, includeMethodLevel = true)
    throw throwable
  }

  override fun handleBeforeAllMethodExecutionException(context: ExtensionContext, throwable: Throwable) {
    // `@BeforeAll` is a class-level method: no per-test state exists, only class-level filters apply.
    abortIfIdeErrorMatches(context, throwable, includeMethodLevel = false)
    throw throwable
  }

  override fun handleAfterAllMethodExecutionException(context: ExtensionContext, throwable: Throwable) {
    // `@AfterAll` is a class-level method: no per-test state exists, only class-level filters apply.
    abortIfIdeErrorMatches(context, throwable, includeMethodLevel = false)
    throw throwable
  }

  /**
   * Throws [TestAbortedException] (downgrading the failure to IGNORED) when a captured IDE error
   * matches an applicable filter. Returns normally when nothing matches, so the caller can rethrow.
   */
  private fun abortIfIdeErrorMatches(context: ExtensionContext, throwable: Throwable, includeMethodLevel: Boolean) {
    val store = context.getStore(NAMESPACE)

    if (includeMethodLevel) {
      val methodFilters = collectMethodLevelFilters(context)
      if (methodFilters.isNotEmpty()) {
        val testState = store.get(TEST_STATE_KEY, State::class.java)
        checkFiltersAndThrow(methodFilters, testState?.collectErrors().orEmpty(), throwable)
      }
    }

    val classFilters = collectClassLevelFilters(context)
    if (classFilters.isNotEmpty()) {
      val classState = store.get(CLASS_STATE_KEY, State::class.java)
      checkFiltersAndThrow(classFilters, classState?.collectErrors().orEmpty(), throwable)
    }
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
