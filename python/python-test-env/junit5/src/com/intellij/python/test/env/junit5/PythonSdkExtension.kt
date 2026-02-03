// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.test.env.junit5

import com.intellij.openapi.projectRoots.Sdk
import com.intellij.python.junit5Tests.framework.env.PythonSdk
import kotlinx.coroutines.runBlocking
import org.jetbrains.annotations.ApiStatus.Internal
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.ParameterContext
import org.junit.jupiter.api.extension.ParameterResolver

/**
 * Extension that provides [Sdk] parameter annotated with [PythonSdk].
 * Retrieves the Python environment initialized by [RunOnEnvironmentsExtension] and creates SDK only once.
 */
@Internal
internal class PythonSdkExtension : ParameterResolver, AfterAllCallback {

  private val namespace = ExtensionContext.Namespace.create(PythonSdkExtension::class.java)
  private val sdkKey = "pythonSdk"

  override fun supportsParameter(parameterContext: ParameterContext, extensionContext: ExtensionContext): Boolean {
    return parameterContext.parameter.type == Sdk::class.java &&
           parameterContext.parameter.isAnnotationPresent(PythonSdk::class.java)
  }

  override fun resolveParameter(parameterContext: ParameterContext, extensionContext: ExtensionContext): Sdk {
    return extensionContext.getStore(namespace).getOrComputeIfAbsent(sdkKey, {
      createSdk(extensionContext)
    }, Sdk::class.java)
  }

  private fun createSdk(extensionContext: ExtensionContext): Sdk {
    val pythonEnv = RunOnEnvironmentsExtension.getPythonEnvironment(extensionContext)
    val sdk = runBlocking {
      pythonEnv.prepareSdk()
    }
    return sdk
  }

  override fun afterAll(context: ExtensionContext) {
    context.getStore(namespace)?.remove(sdkKey)
  }
}
