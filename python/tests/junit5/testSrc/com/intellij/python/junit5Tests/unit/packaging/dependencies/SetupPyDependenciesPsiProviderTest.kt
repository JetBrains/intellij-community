// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit.packaging.dependencies

import com.intellij.openapi.application.readAction
import com.intellij.psi.PsiFileFactory
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.jetbrains.python.PythonFileType
import com.jetbrains.python.packaging.setupPy.SetupPyDependenciesPsiProvider
import com.jetbrains.python.psi.PyFile
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@TestApplication
internal class SetupPyDependenciesPsiProviderTest {
  private val projectFixture = projectFixture()
  private val provider = SetupPyDependenciesPsiProvider()

  @Test
  fun `collects install_requires entries`() {
    val names = declaredNames(
      "setup.py",
      """
      from setuptools import setup
      setup(
          name="demo",
          install_requires=["requests==2.31.0", "flask", "Django>=4.0"],
      )
      """.trimIndent(),
    )
    assertEquals(setOf("requests", "flask", "django"), names)
  }

  @Test
  fun `preserves requirement extras`() {
    val names = declaredNames(
      "setup.py",
      """
      from setuptools import setup
      setup(install_requires=["uvicorn[standard]>=0.35.0"])
      """.trimIndent(),
    )
    assertEquals(setOf("uvicorn"), names)
  }

  @Test
  fun `ignores files without setup call`() {
    assertTrue(declaredNames("setup.py", "x = [\"requests==2.31.0\"]\n").isEmpty())
  }

  @Test
  fun `ignores setup call without install_requires`() {
    val names = declaredNames(
      "setup.py",
      """
      from setuptools import setup
      setup(name="demo", version="1.0")
      """.trimIndent(),
    )
    assertTrue(names.isEmpty())
  }

  @Test
  fun `does not resolve install_requires variable reference`() {
    val names = declaredNames(
      "setup.py",
      """
      from setuptools import setup
      REQS = ["requests==2.31.0"]
      setup(install_requires=REQS)
      """.trimIndent(),
    )
    assertTrue(names.isEmpty())
  }

  @Test
  fun `ignores non-setup_py files`() {
    val names = declaredNames(
      "conftest.py",
      """
      from setuptools import setup
      setup(install_requires=["requests==2.31.0"])
      """.trimIndent(),
    )
    assertTrue(names.isEmpty())
  }

  private fun declaredNames(fileName: String, text: String): Set<String> = runBlocking {
    readAction {
      val file = PsiFileFactory.getInstance(projectFixture.get())
        .createFileFromText(fileName, PythonFileType.INSTANCE, text) as PyFile
      provider.getDependencies(file).orEmpty().keys.mapTo(mutableSetOf()) { it.name }
    }
  }
}
