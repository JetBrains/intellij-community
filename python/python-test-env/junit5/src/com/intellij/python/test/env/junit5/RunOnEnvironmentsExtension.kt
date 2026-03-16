// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.test.env.junit5

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.process.ProcessNotCreatedException
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.python.community.impl.pipenv.pipenvPath
import com.intellij.python.community.impl.poetry.common.poetryPath
import com.intellij.python.junit5Tests.framework.env.PyEnvTestCase
import com.intellij.python.junit5Tests.framework.env.RunOnEnvironments
import com.intellij.python.junit5Tests.framework.resolvePythonTool
import com.intellij.python.test.env.core.PyEnvironment
import com.intellij.python.test.env.core.PyEnvironmentSpec
import com.intellij.util.containers.orNull
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.sdk.impl.resolvePythonHome
import kotlinx.coroutines.runBlocking
import org.jetbrains.annotations.ApiStatus.Internal
import org.junit.jupiter.api.extension.ClassTemplateInvocationContext
import org.junit.jupiter.api.extension.ClassTemplateInvocationContextProvider
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.TestTemplateInvocationContext
import org.junit.jupiter.api.extension.TestTemplateInvocationContextProvider
import org.junit.platform.commons.support.AnnotationSupport
import java.nio.file.Path
import java.util.stream.Stream
import kotlin.streams.asStream

/**
 * Extension that initializes Python environment based on tags from [@PyEnvTestCase][PyEnvTestCase] annotation.
 * Other extensions can retrieve the initialized environment from the extension context.
 */
@Internal
class RunOnEnvironmentsExtension : TestTemplateInvocationContextProvider, ClassTemplateInvocationContextProvider {

  companion object {
    private val LOG = Logger.getInstance(RunOnEnvironmentsExtension::class.java)
    private val checkedTools = mutableMapOf<String, MutableSet<Path>>()

    val namespace: ExtensionContext.Namespace = ExtensionContext.Namespace.create(RunOnEnvironmentsExtension::class.java)
    const val envKey = "pythonEnv"

    fun getPythonEnvironment(extensionContext: ExtensionContext): PyEnvironment {
      return extensionContext.getStore(namespace).get(envKey, PyEnvironment::class.java)
             ?: getOrCreatePythonEnvironment(extensionContext)
    }

    fun getOrCreatePythonEnvironment(extensionContext: ExtensionContext): PyEnvironment {
      val annotation = findPyEnvTestCaseAnnotation(extensionContext)
                       ?: throw IllegalStateException("Python environment not initialized. Make sure @PyEnvTestCase is present.")
      return getOrCreatePythonEnvironment(extensionContext, annotation.env.spec)
    }

    private fun findRunOnEnvironmentAnnotation(context: ExtensionContext): RunOnEnvironments? =
      context.testMethod.flatMap {
        AnnotationSupport.findAnnotation(context.requiredTestMethod, RunOnEnvironments::class.java)
      }.or {
        AnnotationSupport.findAnnotation(context.requiredTestClass, RunOnEnvironments::class.java)
      }.orNull()

    private fun findPyEnvTestCaseAnnotation(context: ExtensionContext): PyEnvTestCase? =
      context.testMethod.flatMap {
        AnnotationSupport.findAnnotation(context.requiredTestMethod, PyEnvTestCase::class.java)
      }.or {
        AnnotationSupport.findAnnotation(context.requiredTestClass, PyEnvTestCase::class.java)
      }.orNull()

    private fun getOrCreatePythonEnvironment(context: ExtensionContext, spec: PyEnvironmentSpec<*>): PyEnvironment {
      return context.getStore(namespace).getOrComputeIfAbsent(envKey, {
        createPythonEnvironment(context, spec)
      }, PyEnvironment::class.java)
    }

    private fun createPythonEnvironment(context: ExtensionContext, envSpec: PyEnvironmentSpec<*>): PyEnvironment {
      val factory = getOrCreatePyEnvironmentFactory(context)
      val env = runBlocking {
        factory.createEnvironment(envSpec)
      }

      // Configure poetry, pipenv, and uv.
      // Only detect tool paths from this environment if not already configured
      // by other extensions (e.g. @RequiresPoetry), to avoid overwriting a valid
      // path with a potentially broken one from an environment that doesn't ship the tool.
      val pythonBinary = env.pythonPath
      checkAndGetToolPath(pythonBinary, "poetry", false)?.let { PropertiesComponent.getInstance().poetryPath = it }
      checkAndGetToolPath(pythonBinary, "pipenv", false)?.let { PropertiesComponent.getInstance().pipenvPath = it }

      val uv = pythonBinary.resolvePythonHome().resolvePythonTool("uv")
      PropertiesComponent.getInstance().setValue("PyCharm.Uv.Path", uv.toString())

      return env
    }

    private fun checkAndGetToolPath(env: PythonBinary, toolName: String, toThrow: Boolean): String? {
      val tool = env.resolvePythonHome().resolvePythonTool(toolName)
      if (checkedTools[toolName]?.contains(tool) != true) {
        val output = try {
          CapturingProcessHandler(GeneralCommandLine(tool.toString(), "--version")).runProcess(60_000, true)
        }
        catch (e: ProcessNotCreatedException) {
          val message = "Tool ${toolName} not found at $tool. Make sure it's installed in the Python environment."
          if (toThrow) {
            throw AssertionError(message, e)
          }
          else {
            LOG.warn(message)
            return null
          }
        }
        assert(output.exitCode == 0) { "$tool seems to be broken, output: $output. For Windows check `fix_path.cmd`" }
        LOG.info("${toolName} found at $tool")
        checkedTools.compute(toolName) { _, v -> (v ?: mutableSetOf()).also { it.add(tool) } }
      }

      return tool.toString()
    }

  }

  override fun supportsTestTemplate(context: ExtensionContext): Boolean {
    val annotation = AnnotationSupport.findAnnotation(context.requiredTestMethod, PyEnvTestCase::class.java).orNull()
    return annotation != null
  }

  override fun provideTestTemplateInvocationContexts(context: ExtensionContext): Stream<TestTemplateInvocationContext> {
    val annotation = findRunOnEnvironmentAnnotation(context) ?: error("No @RunOnEnvironment annotation found")
    return annotation.envs.map { env ->
      object : TestTemplateInvocationContext {
        override fun getDisplayName(invocationIndex: Int): String {
          return "Python ${env.spec.pythonVersion}"
        }

        override fun prepareInvocation(context: ExtensionContext) {
          getOrCreatePythonEnvironment(context, env.spec)
        }
      }
    }.asSequence().asStream()
  }

  override fun supportsClassTemplate(context: ExtensionContext): Boolean {
    val annotation = AnnotationSupport.findAnnotation(context.requiredTestClass, RunOnEnvironments::class.java).orNull()
    return annotation != null
  }

  override fun provideClassTemplateInvocationContexts(context: ExtensionContext): Stream<out ClassTemplateInvocationContext> {
    val annotation = AnnotationSupport.findAnnotation(context.requiredTestClass, RunOnEnvironments::class.java).orElseThrow()
    return annotation.envs.map { env ->
      object : ClassTemplateInvocationContext {
        override fun getDisplayName(invocationIndex: Int): String {
          return "Python ${env.spec.pythonVersion}"
        }

        override fun prepareInvocation(context: ExtensionContext) {
          getOrCreatePythonEnvironment(context, env.spec)
        }
      }
    }.asSequence().asStream()
  }
}
