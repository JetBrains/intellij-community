package com.intellij.lambda.testFramework.junit

import com.intellij.lambda.testFramework.utils.BackgroundRunWithLambda
import com.intellij.tools.ide.util.common.logOutput
import com.intellij.util.containers.orNull
import org.junit.jupiter.api.extension.*
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.ArgumentsProvider
import org.junit.jupiter.params.provider.ArgumentsSource
import org.junit.platform.commons.util.AnnotationUtils
import java.util.*
import java.util.function.Function
import java.util.stream.Stream

// For the already existing tests that use test parametrization (and rewriting them to use @CartesianTest isn't desirable)
class MonolithAndSplitModeContextProvider : TestTemplateInvocationContextProvider {
  override fun supportsTestTemplate(context: ExtensionContext): Boolean = getModesToRun(context).isNotEmpty()

  private fun getModesToRun(context: ExtensionContext): List<IdeRunMode> {
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

  override fun provideTestTemplateInvocationContexts(context: ExtensionContext): Stream<TestTemplateInvocationContext> {
    val modesToRun = getModesToRun(context)
    logOutput("Test will be run in modes: $modesToRun")

    // Test with parameters in the method signature
    val parametrizedRuns = getArgumentsProvider(context).map(Function { provider: ArgumentsProvider ->
      try {
        // PARAMETERIZED CASE: Generate tests for each argument in each mode
        provider.provideArguments(context).flatMap { args: Arguments ->
          modesToRun.stream().map { mode -> createInvocationContext(mode, args.get(), context) }
        }
      }
      catch (e: Exception) {
        throw RuntimeException(e)
      }
    })
    if (parametrizedRuns.isPresent) return parametrizedRuns.get()

    // SIMPLE CASE - test without parameters: Generate one test for each mode
    return modesToRun.stream().map { mode -> createInvocationContext(mode, emptyArray(), context) }
  }

  private fun createInvocationContext(mode: IdeRunMode, args: Array<Any>, context: ExtensionContext): TestTemplateInvocationContext {
    return object : TestTemplateInvocationContext {
      override fun getDisplayName(invocationIndex: Int): String {
        startIdeWhenDefaultJUnitEngineIsUsed(mode)

        val params = if (args.isNotEmpty()) " with params: " + listOf(*args) else ""
        return if (params.isNotEmpty()) "[$mode] $params" else "[$mode]"
      }

      override fun getAdditionalExtensions(): MutableList<Extension> {
        return mutableListOf(object : ParameterResolver {
          override fun supportsParameter(paramCtx: ParameterContext, extCtx: ExtensionContext): Boolean {
            val type = paramCtx.parameter.type
            return type.isAssignableFrom(BackgroundRunWithLambda::class.java) ||
                   type.isAssignableFrom(mode::class.java) ||
                   (paramCtx.index > 0 && paramCtx.index - 1 < args.size)
          }

          override fun resolveParameter(paramCtx: ParameterContext, extCtx: ExtensionContext): Any {
            return when {
              paramCtx.parameter.type.isAssignableFrom(mode::class.java) -> mode
              paramCtx.parameter.type.isAssignableFrom(BackgroundRunWithLambda::class.java) -> {
                startIdeWhenDefaultJUnitEngineIsUsed(mode)
                IdeInstance.ideBackgroundRun
              }
              else -> {
                // The first parameter is the IdeRunMode, so offset argument index by 1
                args[paramCtx.index - 1]
              }
            }
          }
        })
      }
    }
  }

  private fun getArgumentsProvider(context: ExtensionContext): Optional<ArgumentsProvider> {
    return AnnotationUtils.findAnnotation(context.testMethod, ArgumentsSource::class.java)
      .map { obj: ArgumentsSource -> obj.value }
      .map { clazz -> this.instantiateProvider(clazz.java) }
  }

  private fun instantiateProvider(clazz: Class<out ArgumentsProvider>): ArgumentsProvider {
    try {
      return clazz.getDeclaredConstructor().newInstance()
    }
    catch (e: Exception) {
      throw RuntimeException("Could not instantiate ArgumentsProvider: " + clazz.getName(), e)
    }
  }

  private fun startIdeWhenDefaultJUnitEngineIsUsed(mode: IdeRunMode) {
    // support default JUnit Jupiter engine execution
    if (!isGroupedExecutionEnabled) IdeInstance.startIde(mode)
  }
}