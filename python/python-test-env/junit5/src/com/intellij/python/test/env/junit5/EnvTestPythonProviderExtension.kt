// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.test.env.junit5

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Disposer
import com.intellij.python.community.services.systemPython.SystemPythonProvider
import com.intellij.python.test.env.common.EnvTestPythonProvider
import com.intellij.testFramework.registerExtension
import org.jetbrains.annotations.ApiStatus.Internal
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.ExtensionContext

/**
 * JUnit5 extension that registers [EnvTestPythonProvider] as an application-level service
 * before all tests and automatically deregisters it after all tests complete.
 *
 * The provider is registered once per test class in the root context.
 *
 * Usage:
 * ```
 * @ExtendWith(EnvTestPythonProviderExtension::class)
 * class MyTest {
 *   @Test
 *   fun test() {
 *     // EnvTestPythonProvider is available as SystemPythonProvider
 *   }
 * }
 * ```
 */
@Internal
class EnvTestPythonProviderExtension : BeforeAllCallback {

  override fun beforeAll(context: ExtensionContext) {
    val rootContext = context.root
    val namespace = ExtensionContext.Namespace.create(EnvTestPythonProviderExtension::class.java)
    
    rootContext.getStore(namespace).getOrComputeIfAbsent("provider") {
      val factory = getOrCreatePyEnvironmentFactory(context)
      val provider = EnvTestPythonProvider(factory)
      val disposable = Disposer.newDisposable("EnvTestPythonProvider")

      ApplicationManager.getApplication().registerExtension(
        SystemPythonProvider.EP,
        provider,
        disposable
      )
      ProviderResource(disposable)
    }
  }

  private class ProviderResource(private val disposable: Disposable) : AutoCloseable {
    override fun close() {
      Disposer.dispose(disposable)
    }
  }
}
