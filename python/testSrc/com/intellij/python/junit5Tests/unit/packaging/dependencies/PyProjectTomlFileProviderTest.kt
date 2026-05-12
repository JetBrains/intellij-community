// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit.packaging.dependencies

import com.intellij.python.pyproject.PyProjectTomlFile
import com.intellij.python.pyproject.PyProjectTomlFileProvider
import com.intellij.testFramework.LightVirtualFile
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

internal class PyProjectTomlFileProviderTest {
  private val provider = PyProjectTomlFileProvider()

  @Test
  fun `recognises pyproject_toml`() = runBlocking {
    val file = LightVirtualFile("pyproject.toml")
    assertEquals(PyProjectTomlFile(file), provider.fromFile(file))
  }

  @ParameterizedTest
  @ValueSource(strings = ["pyproject.cfg", "PyProject.toml", "project.toml", "pyproject.yml", "setup.py", "requirements.txt"])
  fun `rejects non-matching names`(name: String) = runBlocking {
    assertNull(provider.fromFile(LightVirtualFile(name)))
  }
}
