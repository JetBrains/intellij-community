package com.intellij.lambda.testFramework.junit

import com.intellij.lambda.testFramework.starter.IdeInstance
import com.intellij.lambda.testFramework.utils.BackgroundRunWithLambda
import com.intellij.util.containers.orNull
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.ParameterContext
import org.junit.jupiter.api.extension.ParameterResolver
import org.junit.jupiter.params.ParameterizedTest

/**
 * Extension to support @ParameterizedTest with @ExecuteInMonolithAndSplitMode.
 *
 * This ensures the IDE is started in the correct mode before each parameterized test invocation.
 */
class MonolithAndSplitModeParameterizedTestSupport : BeforeEachCallback, ParameterResolver {

  override fun beforeEach(context: ExtensionContext) {
    // Only handle @ParameterizedTest methods
    val isParameterized = context.testMethod.orNull()?.isAnnotationPresent(ParameterizedTest::class.java) == true
    if (!isParameterized) return

    //Start IDE based on the mode filter from configuration
    val modeFilter = context.getConfigurationParameter("ide.run.mode.filter").orNull()
    if (modeFilter != null) {
      val mode = IdeRunMode.valueOf(modeFilter)
      IdeInstance.startIde(mode)
    }
  }

  override fun supportsParameter(parameterContext: ParameterContext, extensionContext: ExtensionContext): Boolean {
    // Support BackgroundRunWithLambda parameter for parameterized tests
    val isParameterized = extensionContext.testMethod.orNull()?.isAnnotationPresent(ParameterizedTest::class.java) == true
    return isParameterized && parameterContext.parameter.type.isAssignableFrom(BackgroundRunWithLambda::class.java)
  }

  override fun resolveParameter(parameterContext: ParameterContext, extensionContext: ExtensionContext): Any {
    return IdeInstance.ideBackgroundRun
  }
}