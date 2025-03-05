// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.env.systemPython.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.python.community.impl.venv.createVenv
import com.intellij.python.community.services.systemPython.SystemPythonProvider
import com.intellij.python.community.services.systemPython.SystemPythonService
import com.intellij.python.community.services.systemPython.UICustomization
import com.intellij.python.junit5Tests.framework.env.PyEnvTestCase
import com.intellij.python.junit5Tests.framework.env.PythonBinaryPath
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.registerExtension
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.getOrThrow
import io.mockk.coEvery
import io.mockk.mockk
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

@PyEnvTestCase
class EnvProviderTest {

  @Test
  fun testPythonProvider(@PythonBinaryPath python: PythonBinary): Unit = timeoutRunBlocking {
    val systemPythons = SystemPythonService().findSystemPythons()
    val systemPythonBinaries = systemPythons.map { it.pythonBinary }
    assertThat("No env python registered", systemPythonBinaries, Matchers.hasItem(python))

    if (systemPythons.size > 1) {
      val best = systemPythons.first()
      for (python in systemPythons.subList(1, systemPythonBinaries.size)) {
        assertTrue(python.languageLevel <= best.languageLevel, "$best is the first, bust worse than $python")
      }
    }
  }

  @Test
  fun testProviderWithUi(
    @TestDisposable disposable: Disposable,
    @PythonBinaryPath python: PythonBinary,
    @TempDir venvDir: Path,
  ): Unit = timeoutRunBlocking {
    val venvPython = createVenv(python, venvDir).getOrThrow()
    val ui = UICustomization("myui")
    val provider = mockk<SystemPythonProvider>()
    coEvery { provider.findSystemPythons(any()) } returns Result.success(setOf(venvPython))
    coEvery { provider.uiCustomization } returns ui
    ApplicationManager.getApplication().registerExtension(SystemPythonProvider.EP, provider, disposable)
    val python = SystemPythonService().findSystemPythons().first { it.pythonBinary == venvPython }
    assertEquals(ui, python.ui, "Wrong UI")
  }
}