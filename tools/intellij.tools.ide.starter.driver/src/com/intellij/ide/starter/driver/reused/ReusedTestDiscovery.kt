package com.intellij.ide.starter.driver.reused

import com.intellij.driver.client.Driver
import com.intellij.ide.starter.driver.engine.BackgroundRun
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.TestMethodOrder
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.ParameterContext
import org.junit.platform.commons.annotation.Testable
import org.junit.platform.commons.support.AnnotationSupport
import java.lang.reflect.Method
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.toDuration
import kotlin.time.toDurationUnit

// Reflection over a @ReusedRun test class: which parameters ReusedExtension injects, and the rules such a class must follow.

internal fun isReusedIdeParameter(parameterType: Class<*>, testClass: Class<*>): Boolean =
  parameterType == Driver::class.java ||
  // Only resolvable for the framework's own self-tests, see isTestFrameworkTestPackage.
  (parameterType in SELF_TEST_PARAMETERS && testClass.isTestFrameworkTestPackage())

internal fun Class<*>.isTestFrameworkTestPackage(): Boolean =
  packageName in setOf(REUSED_RUN_SELF_TEST_PACKAGE, REPORT_TEST_PACKAGE)

internal fun ParameterContext.isBeforeAllMethodParameter(): Boolean =
  (parameter.declaringExecutable as? Method)?.isAnnotationPresent(BeforeAll::class.java) == true

internal fun forbidOrderedExecution(context: ExtensionContext) {
  val testClass = context.requiredTestClass
  if (testClass.isTestFrameworkTestPackage()) {
    return
  }

  check(!testClass.isAnnotationPresent(TestMethodOrder::class.java)) {
    "@${ReusedRun::class.simpleName} does not support @${TestMethodOrder::class.simpleName} on ${testClass.name}. " +
    "The IDE state is restored between tests, so tests must not depend on execution order."
  }

  val orderedMethods = testClass.orderedMethods()
  check(orderedMethods.isEmpty()) {
    val methodNames = orderedMethods.joinToString { it.name }
    "@${ReusedRun::class.simpleName} does not support @${Order::class.simpleName} on ${testClass.name} methods: $methodNames. " +
    "The IDE state is restored between tests, so tests must not depend on execution order."
  }
}

internal fun forbidNestedClass(context: ExtensionContext) {
  val testClass = context.requiredTestClass
  check(!testClass.isAnnotationPresent(Nested::class.java)) {
    "@${ReusedRun::class.simpleName} does not support @${Nested::class.simpleName} on ${testClass.name}. " +
    "Move its tests to a separate top-level @${ReusedRun::class.simpleName} class."
  }
}

private fun Class<*>.orderedMethods(): List<Method> =
  selfAndSuperclasses()
    .flatMap { it.declaredMethods.asSequence() }
    .filter { it.isAnnotationPresent(Order::class.java) }
    .sortedBy { it.name }
    .toList()

internal fun Class<*>.selfAndSuperclasses(): Sequence<Class<*>> =
  generateSequence(this) { it.superclass }.takeWhile { it != Any::class.java }

/**
 * How long the IDE process of a [ReusedRun] class may live.
 *
 * One process serves every test of the class, so the budget is one IDE start plus the timeout of each test.
 * A test without its own `@Timeout` takes the class `@Timeout`, else [DEFAULT_TEST_TIMEOUT].
 * A parameterized or a repeated test counts once.
 */
internal fun ideRunTimeout(testClass: Class<*>): Duration {
  val classTimeout = AnnotationSupport.findAnnotation(testClass, Timeout::class.java)
                       .map { it.asDuration() }
                       .orElse(DEFAULT_TEST_TIMEOUT)
  return testClass.testMethods().fold(IDE_START_TIMEOUT) { total, method ->
    total + (method.getDeclaredAnnotation(Timeout::class.java)?.asDuration() ?: classTimeout)
  }
}

private fun Timeout.asDuration() = value.toDuration(unit.toDurationUnit())

private fun Class<*>.testMethods(): Sequence<Method> =
  selfAndSuperclasses()
    .flatMap { it.declaredMethods.asSequence() }
    .filter { AnnotationSupport.isAnnotated(it, Testable::class.java) }
    // an override of a superclass test runs once
    .distinctBy { it.name to it.parameterTypes.toList() }

private val IDE_START_TIMEOUT = 10.minutes
private val DEFAULT_TEST_TIMEOUT = 10.minutes

private val SELF_TEST_PARAMETERS = setOf(BackgroundRun::class.java)

private const val REUSED_RUN_SELF_TEST_PACKAGE = "com.intellij.ide.starter.driver.reused"
private const val REPORT_TEST_PACKAGE = "com.intellij.ide.starter.extended.report"
