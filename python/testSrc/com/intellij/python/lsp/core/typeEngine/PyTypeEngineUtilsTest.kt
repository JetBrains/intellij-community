// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.lsp.core.typeEngine

import com.intellij.idea.TestFor
import com.intellij.python.junit5Tests.framework.pyModuleFixture
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.jetbrains.python.junit5.framework.pyMockSdkFixture
import com.jetbrains.python.tools.sdkTools.PythonMockSdk
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@TestApplication
@TestFor(issues = ["PY-91402"])
internal class PyTypeEngineUtilsTest {
  private val projectFixture = projectFixture(openAfterCreation = true)

  private val project get() = projectFixture.get()

  @Nested
  inner class SingleModule {
    private val mainModule = projectFixture.pyModuleFixture("main")
    private val mainSdk = projectFixture.pyMockSdkFixture(mainModule) { PythonMockSdk.create() }

    @Test
    fun `engine is supported without the registry key`() {
      mainSdk.get()
      assertTrue(PyTypeEngineUtils.isExternalTypeEngineSupported(project))
    }
  }

  @Nested
  inner class MultiModule {
    private val mainModule = projectFixture.pyModuleFixture("main")
    private val mainSdk = projectFixture.pyMockSdkFixture(mainModule) { PythonMockSdk.create() }
    private val secondModule = projectFixture.pyModuleFixture("second")

    @Test
    fun `engine is unsupported while the registry key is off`() {
      mainSdk.get()
      secondModule.get()
      assertFalse(PyTypeEngineUtils.isExternalTypeEngineSupported(project))
    }

    @Test
    @RegistryKey(key = PyTypeEngineUtils.MULTI_MODULE_REGISTRY_KEY, value = "true")
    fun `engine is supported once the registry key is on`() {
      mainSdk.get()
      secondModule.get()
      assertTrue(PyTypeEngineUtils.isExternalTypeEngineSupported(project))
    }
  }

  @Nested
  inner class MultiModuleWithoutInterpreter {
    private val mainModule = projectFixture.pyModuleFixture("main")
    private val secondModule = projectFixture.pyModuleFixture("second")

    /** The key only lifts the module-count restriction; a module the engine can actually serve is still required. */
    @Test
    @RegistryKey(key = PyTypeEngineUtils.MULTI_MODULE_REGISTRY_KEY, value = "true")
    fun `engine stays unsupported without a local interpreter`() {
      mainModule.get()
      secondModule.get()
      assertFalse(PyTypeEngineUtils.isExternalTypeEngineSupported(project))
    }
  }
}
