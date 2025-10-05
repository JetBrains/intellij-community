// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.framework.env.impl

import com.intellij.openapi.diagnostic.Logger
import com.intellij.python.community.testFramework.testEnv.PythonType
import kotlinx.coroutines.runBlocking
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.NonNls
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.ParameterContext
import org.junit.jupiter.api.extension.ParameterResolver
import org.opentest4j.TestAbortedException
import kotlin.reflect.KClass


/**
 * Looks for the first env python and provides it as an argument.
 * Looks at the system property that changes skip to failure.
 * To be run by `PythonJUnit5TestsEnv`.
 *
 * To be used as an "adapter" to connect inheritor to JUnit5.
 * [PYTHON_TYPE] creates [ENV] which is then disposed.
 *
 * [lazy]: postpone resource creation until first parameter used.
 * [additionalTags]: only use pythons with these tags
 */
@Internal
abstract class PythonEnvExtensionBase<ENV : Any, PYTHON_TYPE : PythonType<ENV>>(
  private val annotation: KClass<out Annotation>,
  private val pythonType: PYTHON_TYPE,
  private val envType: KClass<ENV>,
  private val lazy: Boolean = true,
  private vararg val additionalTags: @NonNls String,
) : ParameterResolver, BeforeAllCallback {

  protected companion object {
    val LOG = Logger.getInstance(this::class.java)
  }

  private class ResourceWrapper<ENV : Any>(val env: ENV, val closeable: AutoCloseable) : java.lang.AutoCloseable {
    override fun close() {
      closeable.close()
    }
  }

  private val namespace = ExtensionContext.Namespace.create(this::class.java, pythonType, envType)
  private val key = "instance$envType"

  override fun beforeAll(context: ExtensionContext) {
    if (!lazy) {
      createResource(context)
    }
  }

  private fun createEnv(): ResourceWrapper<ENV> {
    val (_, autoClosable, env) = runBlocking {
      pythonType.createSdkClosableEnv(*additionalTags).getOrElse {
        // Logging due to IDEA-356206
        LOG.warn(it)
        val message = "Couldn't find python to run test against ($pythonType , ${additionalTags.toList()})"
        if (System.getProperty("pycharm.env.tests.fail.if.no.python") != null) {
          throw AssertionError(message, it)
        }
        else {
          throw TestAbortedException(message, it)
        }
      }
    }
    try {
      onEnvFound(env)
    }
    catch (e: Throwable) {
      autoClosable.close()
      throw e
    }
    LOG.info("Env for python found at $env")
    return ResourceWrapper(env, autoClosable)
  }

  /**
   * Callback when [ENV] created
   */
  open fun onEnvFound(env: ENV) = Unit


  override fun supportsParameter(parameterContext: ParameterContext, extensionContext: ExtensionContext): Boolean {
    if (parameterContext.parameter.type != envType.java) return false
    return parameterContext.parameter.isAnnotationPresent(annotation.java)
  }


  override fun resolveParameter(parameterContext: ParameterContext, extensionContext: ExtensionContext): ENV = createResource(extensionContext)

  private fun createResource(extensionContext: ExtensionContext): ENV {
    val resourceWrapper = extensionContext.getStore(namespace).getOrComputeIfAbsent(key, { createEnv() }, ResourceWrapper::class.java)
    val env = resourceWrapper.env
    assert(envType.isInstance(env))
    @Suppress("UNCHECKED_CAST")
    return env as ENV
  }
}
