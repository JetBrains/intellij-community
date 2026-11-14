// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit.ruff

import com.intellij.idea.TestFor
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.service
import com.intellij.psi.PsiManager
import com.intellij.python.pytools.backend.PyToolsState
import com.intellij.python.ruff.RuffConfiguration
import com.intellij.python.ruff.RuffImportOptimizer
import com.intellij.python.ruff.RuffPyTool
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.psiFileFixture
import com.intellij.testFramework.junit5.fixture.sourceRootFixture
import com.intellij.testFramework.junit5.fixture.virtualFileFixture
import com.jetbrains.python.PythonLanguage
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@TestFor(issues = ["PY-89147"])
@TestApplication
internal class RuffImportOptimizerTest {
  private val projectFixture = projectFixture()
  private val sourceRootFixture = projectFixture.moduleFixture().sourceRootFixture()
  private val project by projectFixture
  private val pythonFile by sourceRootFixture.psiFileFixture("main.py", "import os\n")
  private val restFile by sourceRootFixture.virtualFileFixture("notes.rst", "Notes\n=====\n\n.. code-block:: python\n\n   import os\n")

  @BeforeEach
  fun enableRuff() {
    PyToolsState.getInstance(project).setEnabled(RuffPyTool.getInstance(), true)
    project.service<RuffConfiguration>().sortImports = true
  }

  @Test
  fun `a Python file gets the Ruff import optimizer`(): Unit = timeoutRunBlocking {
    assertTrue(readAction { RuffImportOptimizer().supports(pythonFile) })
  }

  @Test
  fun `the Python part of a reStructuredText file does not get the Ruff import optimizer`(): Unit = timeoutRunBlocking {
    val pythonPart = readAction {
      PsiManager.getInstance(project).findViewProvider(restFile)!!.getPsi(PythonLanguage.INSTANCE)
    }
    assertNotNull(pythonPart, "The reStructuredText file has no Python part. Is the ReST Python module loaded?")

    assertFalse(readAction { RuffImportOptimizer().supports(pythonPart!!) })
  }
}
