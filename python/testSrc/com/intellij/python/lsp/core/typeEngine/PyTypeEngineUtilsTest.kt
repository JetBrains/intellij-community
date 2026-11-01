// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.lsp.core.typeEngine

import kotlin.io.path.Path
import com.intellij.testFramework.junit5.fixture.pathInProjectFixture
import com.intellij.idea.TestFor
import com.intellij.python.junit5Tests.framework.pyProjectFixture
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.jetbrains.python.junit5.framework.pyMockInterpreterFixture
import com.jetbrains.python.tools.sdkTools.PythonMockSdk
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@TestApplication
@TestFor(issues = ["PY-91402"])
internal class PyTypeEngineUtilsTest {
  private val projectFixture = projectFixture(openAfterCreation = true)

  private val project get() = projectFixture.get()

  @Nested
  inner class SingleModule {
    private val mainPyProject = projectFixture.pyProjectFixture()
    private val mainSdk = projectFixture.pyMockInterpreterFixture(mainPyProject) { PythonMockSdk.create() }

    @Test
    fun `engine is supported without the registry key`() {
      mainSdk.get()
      assertTrue(PyTypeEngineUtils.isExternalTypeEngineSupported(project))
    }
  }

  @Nested
  inner class MultiModule {
    private val mainPyProject = projectFixture.pyProjectFixture()
    private val mainSdk = projectFixture.pyMockInterpreterFixture(mainPyProject) { PythonMockSdk.create() }
    private val secondPyProject = projectFixture.pyProjectFixture(projectFixture.pathInProjectFixture(Path("second")))

    @Test
    @Disabled("AT-3959")
    fun `engine is unsupported while the registry key is off`() {
      mainSdk.get()
      secondPyProject.get()
      assertFalse(PyTypeEngineUtils.isExternalTypeEngineSupported(project))
    }

    @Test
    @RegistryKey(key = PyTypeEngineUtils.MULTI_MODULE_REGISTRY_KEY, value = "true")
    fun `engine is supported once the registry key is on`() {
      mainSdk.get()
      secondPyProject.get()
      assertTrue(PyTypeEngineUtils.isExternalTypeEngineSupported(project))
    }
  }

  @Nested
  inner class MultiModuleWithoutInterpreter {
    private val mainPyProject = projectFixture.pyProjectFixture()
    private val secondPyProject = projectFixture.pyProjectFixture(projectFixture.pathInProjectFixture(Path("second")))

    /** The key only lifts the module-count restriction; a module the engine can actually serve is still required. */
    @Test
    @RegistryKey(key = PyTypeEngineUtils.MULTI_MODULE_REGISTRY_KEY, value = "true")
    fun `engine stays unsupported without a local interpreter`() {
      mainPyProject.get()
      secondPyProject.get()
      assertFalse(PyTypeEngineUtils.isExternalTypeEngineSupported(project))
    }
  }
}
