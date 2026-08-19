package com.intellij.ide.starter.junit5

import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.runner.CurrentTestMethod
import com.intellij.ide.starter.runner.CurrentTestPlan
import com.intellij.ide.starter.runner.TestMethod
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.platform.engine.TestExecutionResult
import org.junit.platform.engine.support.descriptor.MethodSource
import org.junit.platform.launcher.TestExecutionListener
import org.junit.platform.launcher.TestIdentifier
import org.junit.platform.launcher.TestPlan
import org.kodein.di.direct
import org.kodein.di.instance

/**
 * Provides [CurrentTestMethod] to DI.
 *
 * The method is remembered as soon as the JUnit platform starts the test, but announced to listeners only from
 * [beforeEach] - the two are different moments as far as the CI server is concerned.
 */
open class CurrentTestMethodProvider : TestExecutionListener, BeforeEachCallback {

  /**
   * Opens a new [CurrentTestPlan] generation, so per-test bookkeeping can tell a second run of a method from a
   * return to it.
   *
   * The same JVM runs more than one plan whenever a harness keeps its IDE and executes plan after plan against it,
   * and a plan replays the method ids of the previous one verbatim — a `@TestTemplate` invocation id is the same in
   * every plan. Without this boundary the second plan reads as an invalid lifecycle transition and fails every test
   * whose id was already seen.
   */
  override fun testPlanExecutionStarted(testPlan: TestPlan?) {
    CurrentTestPlan.beginNew()
  }

  /**
   * Launcher listeners registered via `META-INF/services` are notified before the ones the test runner passes to
   * `Launcher.execute`, and the runner's TeamCity listener is one of the latter. Its `testStarted` message has not been
   * printed yet, so metadata reported for this test would still attach to the previous one: only remember the method.
   */
  override fun executionStarted(testIdentifier: TestIdentifier?) {
    if (testIdentifier?.isTest != true) {
      return
    }

    val methodSource = testIdentifier.source.orElse(null) as? MethodSource ?: return

    // TODO: include here current argument (for test template, dynamic tests, parametrized tests)
    di.direct.instance<CurrentTestMethod>().set(
      TestMethod(
        name = methodSource.methodName,
        displayName = testIdentifier.displayName,
        testClass = methodSource.javaClass,
        id = testIdentifier.uniqueId,
      )
    )
  }

  /**
   * The JUnit platform runs before-each callbacks only after every launcher listener has seen `executionStarted`, so the
   * TeamCity test is open by now and listeners may report metadata that attaches to it.
   */
  override fun beforeEach(context: ExtensionContext) {
    di.direct.instance<CurrentTestMethod>().publishToListeners()
  }

  override fun executionFinished(testIdentifier: TestIdentifier?, testExecutionResult: TestExecutionResult?) {
    if (testIdentifier?.isTest == true) {
      di.direct.instance<CurrentTestMethod>().set(null)
    }
  }
}
