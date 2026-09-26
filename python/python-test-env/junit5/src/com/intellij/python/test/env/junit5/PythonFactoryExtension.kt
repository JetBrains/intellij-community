// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.test.env.junit5

import com.intellij.python.test.env.common.createPyEnvironmentFactory
import com.intellij.python.test.env.core.PyEnvironmentFactory
import org.jetbrains.annotations.ApiStatus.Internal
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.ParameterContext
import org.junit.jupiter.api.extension.ParameterResolver

/**
 * Retrieves or creates a [PyEnvironmentFactory] from the root extension context.
 * The factory is stored in the root context and shared across all tests.
 * It will be automatically closed when the root context is closed (since it implements [AutoCloseable]).
 *
 * This utility function can be used by any JUnit5 extension or test that needs access to the factory.
 *
 * @param context The extension context from which to retrieve or create the factory
 * @return The shared [PyEnvironmentFactory] instance
 */
@Internal
fun getOrCreatePyEnvironmentFactory(context: ExtensionContext): PyEnvironmentFactory {
  val rootContext = context.root
  val namespace = ExtensionContext.Namespace.create(PythonFactoryExtension::class.java)
  return rootContext.getStore(namespace)
    .getOrComputeIfAbsent("pyEnvironmentFactory", 
      { createPyEnvironmentFactory() }, 
      PyEnvironmentFactory::class.java)
}

/**
 * JUnit5 extension that manages [PyEnvironmentFactory] lifecycle.
 * Creates a single factory instance in the root context and injects it into test parameters.
 * The factory is automatically closed when the root context is closed.
 *
 * Usage:
 * ```
 * @ExtendWith(PythonFactoryExtension::class)
 * class MyTest {
 *   @Test
 *   fun test(factory: PyEnvironmentFactory) {
 *     // use factory
 *   }
 * }
 * ```
 */
@Internal
class PythonFactoryExtension : ParameterResolver {

  override fun supportsParameter(parameterContext: ParameterContext, extensionContext: ExtensionContext): Boolean {
    return parameterContext.parameter.type == PyEnvironmentFactory::class.java
  }

  override fun resolveParameter(parameterContext: ParameterContext, extensionContext: ExtensionContext): Any {
    return getOrCreatePyEnvironmentFactory(extensionContext)
  }
}
