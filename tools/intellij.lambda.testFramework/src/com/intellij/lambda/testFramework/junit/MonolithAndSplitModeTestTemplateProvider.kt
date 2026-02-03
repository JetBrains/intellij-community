package com.intellij.lambda.testFramework.junit

import com.intellij.lambda.testFramework.starter.IdeInstance
import com.intellij.tools.ide.util.common.logOutput
import com.intellij.util.containers.orNull
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.TestTemplateInvocationContext
import org.junit.jupiter.api.extension.TestTemplateInvocationContextProvider
import org.junit.jupiter.params.ParameterizedTest
import java.util.stream.Stream
import kotlin.jvm.optionals.getOrNull

class MonolithAndSplitModeTestTemplateProvider : TestTemplateInvocationContextProvider {
  override fun supportsTestTemplate(context: ExtensionContext): Boolean {
    // Don't support @ParameterizedTest - Jupiter will handle those natively
    val isParameterized = context.testMethod.orNull()?.isAnnotationPresent(ParameterizedTest::class.java) == true
    if (isParameterized) {
      logOutput("Skipping ${MonolithAndSplitModeTestTemplateProvider::class.simpleName} for @ParameterizedTest: ${context.testMethod.getOrNull()}")
      return false
    }

    return getModesToRun(context).isNotEmpty()
  }

  override fun provideTestTemplateInvocationContexts(context: ExtensionContext): Stream<TestTemplateInvocationContext> {
    val modesToRun = getModesToRun(context)
    logOutput("Test ${context.testMethod.getOrNull()} will be run in modes: $modesToRun")

    return modesToRun.stream().map { mode -> createInvocationContext(mode) }
  }

  private fun createInvocationContext(mode: IdeRunMode): TestTemplateInvocationContext {
    return object : TestTemplateInvocationContext {
      override fun getDisplayName(invocationIndex: Int): String {
        return if (!isGroupedExecutionEnabled) {
          IdeInstance.startIde(mode)
          "[$mode]"
        }
        else super.getDisplayName(invocationIndex)
      }
    }
  }
}

