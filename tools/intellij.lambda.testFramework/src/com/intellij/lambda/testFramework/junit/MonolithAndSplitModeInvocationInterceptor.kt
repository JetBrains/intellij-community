package com.intellij.lambda.testFramework.junit

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.InvocationInterceptor
import org.junit.jupiter.api.extension.ReflectiveInvocationContext
import java.lang.reflect.Method

/**
 * Wrap test method invocations in lambda that later called on the IDE side.
 */
open class MonolithAndSplitModeInvocationInterceptor : InvocationInterceptor {
  override fun interceptTestTemplateMethod(
    invocation: InvocationInterceptor.Invocation<Void>,
    invocationContext: ReflectiveInvocationContext<Method>,
    extensionContext: ExtensionContext,
  ) {
    intercept<Void>(invocation, invocationContext)
  }

  override fun interceptTestMethod(
    invocation: InvocationInterceptor.Invocation<Void>, invocationContext: ReflectiveInvocationContext<Method>,
    extensionContext: ExtensionContext,
  ) {
    intercept<Void>(invocation, invocationContext)
  }

  override fun <T : Any> interceptTestFactoryMethod(
    invocation: InvocationInterceptor.Invocation<T>, invocationContext: ReflectiveInvocationContext<Method>,
    extensionContext: ExtensionContext,
  ): T {
    return intercept<T>(invocation, invocationContext)
  }

  override fun interceptBeforeEachMethod(
    invocation: InvocationInterceptor.Invocation<Void>,
    invocationContext: ReflectiveInvocationContext<Method>,
    extensionContext: ExtensionContext,
  ) {
    intercept<Void>(invocation, invocationContext)
  }

  private fun <T : Any> intercept(invocation: InvocationInterceptor.Invocation<T>, invocationContext: ReflectiveInvocationContext<Method>): T {
    runBlocking {
      // TODO: init background IDE run before all tests (and provide and option to start IDE for every test)
      MonolithAndSplitModeIdeInstanceInitializer.ideBackgroundRun.runLambda(InjectedLambda::class,
                                                                            params = mapOf(
                                                       "testClass" to invocationContext.targetClass.name,
                                                       "testMethod" to invocationContext.executable.name,
                                                       "methodArguments" to argumentsToString(invocationContext.arguments)
                                                     ))
    }

    invocation.skip()
    // TODO: find a way to deal with JUnit5 test factory (see overrides above)
    return Void.TYPE as T
  }
}


internal const val ARGUMENTS_SEPARATOR = "], ["

internal fun argumentsToString(arguments: List<Any>): String = arguments.joinToString(ARGUMENTS_SEPARATOR, prefix = "[", postfix = "]") { it.toString() }

internal fun argumentsFromString(argumentsString: String): List<Any> = argumentsString.removePrefix("[").removeSuffix("]")
  .split(ARGUMENTS_SEPARATOR).map { it.trim() }


