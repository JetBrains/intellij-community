// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.test.env.junit5

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.python.test.env.core.LATEST_PYTHON_VERSION
import com.intellij.python.test.env.core.PyEnvironment
import com.intellij.python.test.env.uv.UvPyEnvironment
import com.intellij.python.test.env.uv.uvEnvironment
import kotlinx.coroutines.runBlocking
import org.jetbrains.annotations.ApiStatus.Internal
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.opentest4j.TestAbortedException
import java.util.ResourceBundle
import kotlin.io.path.pathString

val LATEST_UV_VERSION: String by lazy {
  ResourceBundle.getBundle("com.intellij.python.test.env.junit5.tools").getString("uv.version")
}

/**
 * Extension that ensures Uv tool is available and configured.
 * 
 * This extension creates an uv environment, stores it in the extension context, and configures the uv path in PropertiesComponent.
 */
@Internal
class RequiresUvExtension : BeforeAllCallback, BeforeEachCallback {
  
  companion object {
    private val LOG = Logger.getInstance(RequiresUvExtension::class.java)
    private val namespace = ExtensionContext.Namespace.create(RequiresUvExtension::class.java)
    private const val UV_ENV_KEY = "uvEnvironment"
  }
  
  override fun beforeAll(context: ExtensionContext) {
    configurePoetry(context)
  }
  
  override fun beforeEach(context: ExtensionContext) {
    configurePoetry(context)
  }
  
  private fun configurePoetry(context: ExtensionContext) {
    val store = context.getStore(namespace)
    val uvEnv = store.getOrComputeIfAbsent(UV_ENV_KEY, {
      createUvEnvironment(context).unwrap()
    }, UvPyEnvironment::class.java)
    
    PropertiesComponent.getInstance().setValue("PyCharm.Uv.Path", uvEnv.uvExecutable.pathString)

    LOG.info("Uv configured at: ${uvEnv.uvExecutable.pathString}")
  }
  
  private fun createUvEnvironment(context: ExtensionContext): PyEnvironment {
    val factory = getOrCreatePyEnvironmentFactory(context)
    val envSpec = uvEnvironment(LATEST_UV_VERSION) {
      pythonVersion = LATEST_PYTHON_VERSION
    }
    
    return runBlocking {
      try {
        factory.createEnvironment(envSpec)
      }
      catch (e: Exception) {
        throw TestAbortedException("Failed to create uv environment: ${e.message}", e)
      }
    }
  }
}
