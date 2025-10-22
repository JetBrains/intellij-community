package com.intellij.lambda.testFramework.junit

import com.intellij.openapi.diagnostic.currentClassLogger
import com.intellij.remoteDev.tests.modelGenerated.LambdaRdIdeType
import org.junit.jupiter.api.extension.*
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.ArgumentsProvider
import org.junit.jupiter.params.provider.ArgumentsSource
import org.junit.platform.commons.util.AnnotationUtils
import java.util.*
import java.util.function.Function
import java.util.function.Supplier
import java.util.stream.Stream

// For the already existing tests that use test parametrization (and rewriting them to use @CartesianTest isn't desirable)
class MonolithAndSplitModeContextProvider : TestTemplateInvocationContextProvider {
  override fun supportsTestTemplate(context: ExtensionContext): Boolean = true

  private fun getModesToRun(context: ExtensionContext): List<LambdaRdIdeType> {
    val methodAnnotation = AnnotationUtils.findAnnotation(context.testMethod, ExecuteInMonolithAndSplitMode::class.java)
    if (methodAnnotation.isPresent) {
      return methodAnnotation.get().mode.toList()
    }

    val classAnnotation = AnnotationUtils.findAnnotation(context.testClass, ExecuteInMonolithAndSplitMode::class.java)

    if (classAnnotation.isPresent) {
      return classAnnotation.get().mode.toList()
    }

    throw IllegalStateException("The test should have ${ExecuteInMonolithAndSplitMode::javaClass.name} annotation")
  }

  override fun provideTestTemplateInvocationContexts(context: ExtensionContext): Stream<TestTemplateInvocationContext> {
    val modesToRun = getModesToRun(context)

    currentClassLogger().info("Test will be run in modes: $modesToRun")

    return getArgumentsProvider(context).map(Function { provider: ArgumentsProvider ->
      try {
        // PARAMETERIZED CASE: Generate tests for each argument in each LambdaRdIdeType
        provider.provideArguments(context).flatMap { args: Arguments ->
          modesToRun.stream().map { mode -> createInvocationContext(mode, args.get()) }
        }
      }
      catch (e: Exception) {
        throw RuntimeException(e)
      }
    }).orElseGet(Supplier { // SIMPLE CASE: Generate one test for each LambdaRdIdeType
      modesToRun.stream().map { mode -> createInvocationContext(mode, arrayOf()) }
    }
    )
  }

  private fun createInvocationContext(mode: LambdaRdIdeType, args: Array<Any>): TestTemplateInvocationContext {
    return object : TestTemplateInvocationContext {
      override fun getDisplayName(invocationIndex: Int): String {
        val params = if (args.isNotEmpty()) " with params: " + listOf(*args) else ""
        return if (params.isNotEmpty()) "[$mode] $params" else "[$mode]"
      }

      override fun getAdditionalExtensions(): MutableList<Extension> {
        return mutableListOf(object : ParameterResolver {
          override fun supportsParameter(paramCtx: ParameterContext, extCtx: ExtensionContext): Boolean {
            val type = paramCtx.parameter.type
            return type == mode::class.java || (paramCtx.index > 0 && paramCtx.index - 1 < args.size)
          }

          override fun resolveParameter(paramCtx: ParameterContext, extCtx: ExtensionContext): Any {
            if (paramCtx.parameter.type == mode::class.java) {
              return mode
            }
            // The first parameter is the LambdaRdIdeType, so offset argument index by 1
            return args[paramCtx.index - 1]
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