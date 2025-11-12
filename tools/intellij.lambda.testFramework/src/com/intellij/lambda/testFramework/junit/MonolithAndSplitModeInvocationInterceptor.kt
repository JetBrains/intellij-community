package com.intellij.lambda.testFramework.junit

import com.intellij.lambda.testFramework.utils.BackgroundRunWithLambda
import com.jetbrains.rd.util.printlnError
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.TestTemplate
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.InvocationInterceptor
import org.junit.jupiter.api.extension.ReflectiveInvocationContext
import org.junit.jupiter.params.ParameterizedTest
import java.lang.reflect.Method

/**
 * Wrap test method invocations in lambda that later called on the IDE side.
 */
open class MonolithAndSplitModeInvocationInterceptor : InvocationInterceptor {
  override fun interceptTestTemplateMethod(
    invocation: InvocationInterceptor.Invocation<Void?>,
    invocationContext: ReflectiveInvocationContext<Method?>,
    extensionContext: ExtensionContext?,
  ) {
    intercept<Void?>(invocation, invocationContext)
  }

  override fun interceptTestMethod(
    invocation: InvocationInterceptor.Invocation<Void?>, invocationContext: ReflectiveInvocationContext<Method?>,
    extensionContext: ExtensionContext?,
  ) {
    intercept<Void?>(invocation, invocationContext)
  }

  override fun <T> interceptTestFactoryMethod(
    invocation: InvocationInterceptor.Invocation<T?>, invocationContext: ReflectiveInvocationContext<Method?>,
    extensionContext: ExtensionContext,
  ): T? {
    return intercept<T?>(invocation, invocationContext)
  }

  override fun interceptBeforeEachMethod(
    invocation: InvocationInterceptor.Invocation<Void?>,
    invocationContext: ReflectiveInvocationContext<Method?>,
    extensionContext: ExtensionContext?,
  ) {
    intercept<Void?>(invocation, invocationContext)
  }

  private fun <T> intercept(invocation: InvocationInterceptor.Invocation<T?>, invocationContext: ReflectiveInvocationContext<Method?>): T? {
    if (invocationContext.arguments.any { it::class == BackgroundRunWithLambda::class }) {
      System.err.println("Test ${invocationContext.executable?.name} has ${BackgroundRunWithLambda::class.qualifiedName} parameter. Test is expected to use it directly.")
      return invocation.proceed()
    }

    val allowedAnnotations = listOf(TestTemplate::class, TestFactory::class, ParameterizedTest::class)

    val isAllowedTest = invocationContext.executable!!.annotations.any {
      it.annotationClass in allowedAnnotations
    }

    val fullMethodName = "${invocationContext.targetClass.name}.${invocationContext.executable?.name}"

    if (!isAllowedTest) {
      printlnError("Method $fullMethodName will not be executed inside IDE. " +
                   "Allowed annotations for test method ${allowedAnnotations.map { it.simpleName }}")
      return invocation.proceed()
    }

    @Suppress("RAW_RUN_BLOCKING")
    runBlocking {
      println("Executing test method $fullMethodName inside IDE in mode ${IdeInstance.currentIdeMode}")

      IdeInstance.ideBackgroundRun.runNamedLambda(InjectedLambda::class,
                                                  params = mapOf(
                                               "testClass" to (invocationContext.targetClass.name ?: ""),
                                               "testMethod" to (invocationContext.executable?.name ?: ""),
                                               "methodArguments" to argumentsToString(invocationContext.arguments)
                                             ))
    }
    invocation.skip()
    return null
  }
}


internal const val ARGUMENTS_SEPARATOR = "], ["

internal fun argumentsToString(arguments: List<Any>): String = arguments.joinToString(ARGUMENTS_SEPARATOR, prefix = "[", postfix = "]") { it.toString() }

internal fun argumentsFromString(argumentsString: String): List<Any> = argumentsString.removePrefix("[").removeSuffix("]")
  .split(ARGUMENTS_SEPARATOR).map { it.trim() }


