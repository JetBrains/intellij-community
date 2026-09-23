package com.intellij.ide.starter.driver.reused

import com.intellij.driver.client.Driver
import com.intellij.ide.starter.driver.engine.BackgroundRun
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.ExtendWith
import java.lang.annotation.Inherited
import java.lang.reflect.Method
import java.util.concurrent.TimeUnit
import kotlin.reflect.KClass

/**
 * Enables reused IDE for a Driver UI test class.
 * Ide process is reused within one test class (so it is profitable to put several tests in one class).
 * Ide configs are also cached, so second test class using the same ReusedProject will be faster,
 * so use the same ReusedProject if possible.
 *
 * Project configuration is resolved from a companion val annotated with [ReusedProject].
 */
@Inherited
@Target(AnnotationTarget.CLASS, AnnotationTarget.ANNOTATION_CLASS)
@Retention(AnnotationRetention.RUNTIME)
@Timeout(value = 10, unit = TimeUnit.MINUTES)
@DisplayNameGeneration(ReusedDisplayNameGenerator::class)
@ExtendWith(ReusedExtension::class)
annotation class ReusedRun(
  val sessionStrategy: KClass<out ReusedSessionStrategy> = ReusedSessionStrategy.Companion.DefaultReusedSessionStrategy::class,
)

/** Hides framework-injected parameters from JUnit display names for [ReusedRun] tests. */
class ReusedDisplayNameGenerator : DisplayNameGenerator.Standard() {
  override fun generateDisplayNameForMethod(
    enclosingInstanceTypes: List<Class<*>>,
    testClass: Class<*>,
    testMethod: Method,
  ): String = generateDisplayName(testMethod)

  @Deprecated("Deprecated in Java")
  override fun generateDisplayNameForMethod(testClass: Class<*>, testMethod: Method): String = generateDisplayName(testMethod)

  private fun generateDisplayName(testMethod: Method): String {
    val displayParameterTypes = testMethod.parameterTypes.filterNot(::isReusedIdeDisplayNameParameter)
    if (displayParameterTypes.isEmpty()) {
      return testMethod.name
    }
    return testMethod.name + displayParameterTypes.joinToString(prefix = "(", postfix = ")") { it.simpleName }
  }
}

private fun isReusedIdeDisplayNameParameter(parameterType: Class<*>): Boolean = when (parameterType) {
  BackgroundRun::class.java, Driver::class.java -> true
  else -> false
}
