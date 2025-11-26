package com.intellij.lambda.testFramework.junit

import com.intellij.ide.starter.coroutine.perTestSupervisorScope
import com.intellij.lambda.testFramework.starter.IdeInstance
import com.intellij.lambda.testFramework.utils.BackgroundRunWithLambda
import com.intellij.tools.ide.util.common.logOutput
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

  private fun <T> intercept(invocation: InvocationInterceptor.Invocation<T?>, invocationContext: ReflectiveInvocationContext<Method?>): T? {
    val fullMethodName = "${invocationContext.targetClass.name}.${invocationContext.executable?.name}"

    logOutput("Executing test method \"$fullMethodName\" inside IDE in mode ${IdeInstance.currentIdeMode} with arguments: ${argumentsToString(invocationContext.arguments)}")

    if (invocationContext.arguments.any { it::class == BackgroundRunWithLambda::class }) {
      logOutput("Test \"$fullMethodName\" has ${BackgroundRunWithLambda::class.qualifiedName} parameter. Test is expected to use it directly.")

      // executing the code from the test as is (it will invoke lambda execution in IDE itself)
      return invocation.proceed()
    }

    @Suppress("RAW_RUN_BLOCKING")
    runBlocking(perTestSupervisorScope.coroutineContext) {
      // TODO: use serialized lambda invocation
      IdeInstance.ideBackgroundRun.runNamedLambda(InjectedLambda::class,
                                                  params = mapOf(
                                                    "testClass" to (invocationContext.targetClass.name ?: ""),
                                                    "testMethod" to (invocationContext.executable?.name ?: ""),
                                                    "methodArguments" to argumentsToString(invocationContext.arguments)
                                                  ))
    }

    // the code from the test is executed on IDE side
    invocation.skip()
    return null
  }
}


internal const val ARGUMENTS_SEPARATOR = "], ["

internal fun argumentsToString(arguments: List<Any>): String = arguments.joinToString(ARGUMENTS_SEPARATOR, prefix = "[", postfix = "]") { it.toString() }

internal fun argumentsFromString(argumentsString: String): List<String> = argumentsString.removePrefix("[").removeSuffix("]")
  .split(ARGUMENTS_SEPARATOR).map { it.trim() }


