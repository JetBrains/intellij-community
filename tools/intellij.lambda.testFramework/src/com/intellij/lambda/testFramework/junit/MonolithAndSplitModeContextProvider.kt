package com.intellij.lambda.testFramework.junit

import com.intellij.lambda.testFramework.utils.BackgroundRunWithLambda
import com.intellij.openapi.diagnostic.currentClassLogger
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
    val methodAnnotation = AnnotationUtils.findAnnotation(context.testMethod, ExecuteInMonolithAndSplitMode::class.java)
    if (methodAnnotation.isPresent) {
      return methodAnnotation.get().mode.toList()
    }

    val classAnnotation = AnnotationUtils.findAnnotation(context.testClass, ExecuteInMonolithAndSplitMode::class.java)

    if (classAnnotation.isPresent) {
      return classAnnotation.get().mode.toList()
    }

    throw IllegalStateException("The test is expected to have ${ExecuteInMonolithAndSplitMode::javaClass.name} annotation")
  }

  override fun provideTestTemplateInvocationContexts(context: ExtensionContext): Stream<TestTemplateInvocationContext> {
    val modesToRun = getModesToRun(context)

    currentClassLogger().info("Test will be run in modes: $modesToRun")

    // Test with parameters in the method signature
    val parametrizedRuns = getArgumentsProvider(context).map(Function { provider: ArgumentsProvider ->
      try {
        // PARAMETERIZED CASE: Generate tests for each argument in each LambdaRdIdeType
        provider.provideArguments(context).flatMap { args: Arguments ->
          modesToRun.stream().map { mode -> createInvocationContext(mode, args.get(), context) }
        }
      }
      catch (e: Exception) {
        throw RuntimeException(e)
      }
    })
    if (parametrizedRuns.isPresent) return parametrizedRuns.get()

    // SIMPLE CASE - test without parameters: Generate one test for each LambdaRdIdeType
    return modesToRun.stream().map { mode -> createInvocationContext(mode, emptyArray(), context) }
  }

  private fun createInvocationContext(mode: IdeRunMode, args: Array<Any>, context: ExtensionContext): TestTemplateInvocationContext {
    return object : TestTemplateInvocationContext {
      override fun getDisplayName(invocationIndex: Int): String {
        MonolithAndSplitModeIdeInstanceInitializer.startIde(mode, context)

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
                MonolithAndSplitModeIdeInstanceInitializer.startIde(mode, context)
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
}