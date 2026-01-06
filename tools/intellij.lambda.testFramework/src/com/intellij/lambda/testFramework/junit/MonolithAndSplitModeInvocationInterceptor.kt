package com.intellij.lambda.testFramework.junit

import com.intellij.ide.starter.coroutine.CommonScope.perTestSupervisorScope
import com.intellij.lambda.testFramework.starter.IdeInstance
import com.intellij.lambda.testFramework.utils.IdeWithLambda
import com.intellij.remoteDev.tests.impl.utils.SerializedLambdaHelper
import com.intellij.remoteDev.tests.modelGenerated.LambdaRdTestActionParameters
import com.intellij.remoteDev.tests.modelGenerated.LambdaRdTestSession
import com.intellij.tools.ide.util.common.logOutput
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.InvocationInterceptor
import org.junit.jupiter.api.extension.ReflectiveInvocationContext
import java.io.Serializable
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

    logOutput("Executing test method \"$fullMethodName\" inside IDE in mode ${IdeInstance.currentIdeMode} with arguments: ${
      argumentsToString(invocationContext.arguments)
    }")

    if (invocationContext.arguments.any { it::class == IdeWithLambda::class }) {
      logOutput("Test \"$fullMethodName\" has ${IdeWithLambda::class.qualifiedName} parameter. Test is expected to use it directly.")

      // executing the code from the test as is (it will invoke lambda execution in IDE itself)
      return invocation.proceed()
    }

    @Suppress("RAW_RUN_BLOCKING")
    runBlocking(perTestSupervisorScope.coroutineContext) {
      // TODO: use serialized lambda invocation
      IdeInstance.ide.runNamedLambda(LambdaRdTestActionParameters(
        reference = InjectedLambda::class.java.canonicalName,
        testClass = invocationContext.targetClass.name ?: "",
        testMethod = invocationContext.executable?.name ?: "",
        methodArgumentssBase64 = invocationContext.arguments.map {
          SerializedLambdaHelper().serialize(it as? Serializable
                                             ?: error("Cannot serialize argument: $it of type ${it::class.simpleName}"))
        }
      ))
    }

    // the code from the test is executed on IDE side
    invocation.skip()
    return null
  }
}


internal suspend fun IdeWithLambda.runNamedLambda(params: LambdaRdTestActionParameters) {
  return rdSession.runNamedLambda(params)
}

internal suspend fun IdeWithLambda.runNamedLambdaInBackend(params: LambdaRdTestActionParameters) {
  return (backendRdSession ?: rdSession).runNamedLambda(params)
}

internal suspend fun LambdaRdTestSession.runNamedLambda(params: LambdaRdTestActionParameters) {
  val protocol = this.protocol
                 ?: error("RD Protocol is not initialized for session. Make sure the IDE connection is established before running tests.")
  runLambda.startSuspending(protocol.lifetime, params)
}

internal const val ARGUMENTS_SEPARATOR = "], ["

internal fun argumentsToString(arguments: List<Any>): String =
  arguments.joinToString(ARGUMENTS_SEPARATOR, prefix = "[", postfix = "]") { it.toString() }


