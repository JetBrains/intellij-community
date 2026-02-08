// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.test.env.junit5

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.python.community.impl.poetry.common.poetryPath
import com.intellij.python.junit5Tests.framework.resolvePythonTool
import com.intellij.python.test.env.core.LATEST_PYTHON_VERSION
import com.intellij.python.test.env.core.PyEnvironment
import com.intellij.python.test.env.plain.pythonEnvironment
import com.jetbrains.python.sdk.impl.resolvePythonHome
import kotlinx.coroutines.runBlocking
import org.jetbrains.annotations.ApiStatus.Internal
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.opentest4j.TestAbortedException

/**
 * Extension that ensures Poetry tool is available and configured.
 * 
 * This extension creates a Python environment with poetry library installed,
 * stores it in the extension context, and configures the Poetry path in PropertiesComponent.
 */
@Internal
class RequiresPoetryExtension : BeforeAllCallback, BeforeEachCallback {
  
  companion object {
    private val LOG = Logger.getInstance(RequiresPoetryExtension::class.java)
    private val namespace = ExtensionContext.Namespace.create(RequiresPoetryExtension::class.java)
    private const val POETRY_ENV_KEY = "poetryEnvironment"
  }
  
  override fun beforeAll(context: ExtensionContext) {
    configurePoetry(context)
  }
  
  override fun beforeEach(context: ExtensionContext) {
    configurePoetry(context)
  }
  
  private fun configurePoetry(context: ExtensionContext) {
    val store = context.getStore(namespace)
    val poetryEnv = store.getOrComputeIfAbsent(POETRY_ENV_KEY, {
      createPoetryEnvironment(context)
    }, PyEnvironment::class.java)
    
    val pythonBinary = poetryEnv.pythonPath
    val poetryPath = pythonBinary.resolvePythonHome().resolvePythonTool("poetry")
    
    PropertiesComponent.getInstance().poetryPath = poetryPath.toString()
    LOG.info("Poetry configured at: $poetryPath")
  }
  
  private fun createPoetryEnvironment(context: ExtensionContext): PyEnvironment {
    val factory = getOrCreatePyEnvironmentFactory(context)
    val envSpec = pythonEnvironment {
      pythonVersion = LATEST_PYTHON_VERSION
      libraries {
        +"poetry==1.8.3"
      }
    }
    
    return runBlocking {
      try {
        factory.createEnvironment(envSpec)
      }
      catch (e: Exception) {
        throw TestAbortedException("Failed to create Python environment with poetry: ${e.message}", e)
      }
    }
  }
}
