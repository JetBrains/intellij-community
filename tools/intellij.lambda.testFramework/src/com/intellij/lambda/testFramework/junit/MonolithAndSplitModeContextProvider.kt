
package com.intellij.lambda.testFramework.junit

import com.intellij.lambda.testFramework.utils.BackgroundRunWithLambda
import com.intellij.tools.ide.util.common.logOutput
import com.intellij.util.containers.orNull
import org.junit.jupiter.api.extension.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.platform.commons.util.AnnotationUtils
import java.lang.reflect.AnnotatedElement
import java.util.stream.Stream
import kotlin.jvm.optionals.getOrNull

fun getModesToRun(annotatedElement: AnnotatedElement?): List<IdeRunMode> {
  if (annotatedElement == null) return emptyList()
  val annotation = AnnotationUtils.findAnnotation(annotatedElement, ExecuteInMonolithAndSplitMode::class.java).orNull()

  return annotation?.mode?.toList() ?: emptyList()
}

fun getModesToRun(context: ExtensionContext): List<IdeRunMode> {
  val annotation = AnnotationUtils.findAnnotation(context.testMethod, ExecuteInMonolithAndSplitMode::class.java).orElse(
    AnnotationUtils.findAnnotation(context.testClass, ExecuteInMonolithAndSplitMode::class.java).orNull()
  )

  if (annotation == null) throw IllegalStateException("The test is expected to have ${ExecuteInMonolithAndSplitMode::javaClass.name} annotation")

  // Check if we're running under GroupByModeTestEngine with a mode filter
  val modeFilter = context.getConfigurationParameter("ide.run.mode.filter").orNull()

  // If mode filter is set, only return that mode
  return if (modeFilter != null) {
    listOf(IdeRunMode.valueOf(modeFilter))
  }
  else {
    annotation.mode.toList()
  }
}

class MonolithAndSplitModeContextProvider : TestTemplateInvocationContextProvider {
  override fun supportsTestTemplate(context: ExtensionContext): Boolean {
    // Don't support @ParameterizedTest - Jupiter will handle those natively
    val isParameterized = context.testMethod.orNull()?.isAnnotationPresent(ParameterizedTest::class.java) == true
    if (isParameterized) {
      logOutput("Skipping MonolithAndSplitModeContextProvider for @ParameterizedTest: ${context.testMethod.orNull()?.name}")
      return false
    }

    return getModesToRun(context).isNotEmpty()
  }

  override fun provideTestTemplateInvocationContexts(context: ExtensionContext): Stream<TestTemplateInvocationContext> {
    val modesToRun = getModesToRun(context)
    logOutput("Test ${context.testMethod.getOrNull()} will be run in modes: $modesToRun")

    // Only handle @TestTemplate tests (not @ParameterizedTest)
    return modesToRun.stream().map { mode -> createInvocationContext(mode, context) }
  }

  private fun createInvocationContext(mode: IdeRunMode, context: ExtensionContext): TestTemplateInvocationContext {
    return object : TestTemplateInvocationContext {
      override fun getDisplayName(invocationIndex: Int): String {
        startIdeForUnitTestsWithInjectedLambdas(mode)

        return if (!isGroupedExecutionEnabled) {
          "[$mode]"
        }
        else super.getDisplayName(invocationIndex)
      }

      override fun getAdditionalExtensions(): MutableList<Extension> {
        return mutableListOf(
          object : ParameterResolver {
            override fun supportsParameter(paramCtx: ParameterContext, extCtx: ExtensionContext): Boolean =
              paramCtx.parameter.type.isAssignableFrom(BackgroundRunWithLambda::class.java)

            override fun resolveParameter(paramCtx: ParameterContext, extCtx: ExtensionContext): Any = IdeInstance.ideBackgroundRun
          })
      }
    }
  }

  private fun startIdeForUnitTestsWithInjectedLambdas(mode: IdeRunMode) {
    IdeInstance.startIde(mode)
  }
}

