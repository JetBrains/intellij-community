// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.grazie

import com.jetbrains.python.allure.Components
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.intellij.grazie.GrazieConfig
import com.intellij.grazie.GrazieTestFixture
import com.intellij.grazie.grazieTestFixture
import com.intellij.grazie.jlanguage.Lang
import com.intellij.grazie.utils.TextStyleDomain
import com.intellij.idea.TestFor
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.jetbrains.python.PythonTestUtil
import org.junit.jupiter.api.Test
import kotlin.io.path.Path

@Subsystems.Inspections
@Components.Grazie
@Layers.Functional
@TestApplication
class PythonGrazieSupportTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val grazie by grazieTestFixture(
    projectFixture,
    testDataPath = Path(PythonTestUtil.getTestDataPath(), "grazie").toString(),
  )

  @Test
  fun `test f-strings`() {
    grazie.runHighlightTestForFile("FStrings.py")
  }

  @Test
  fun `test grammar check in constructs`() {
    grazie.runHighlightTestForFile("Constructs.py")
  }

  @Test
  fun `test grammar check in docs`() {
    grazie.enableProofreadingFor(setOf(Lang.GERMANY_GERMAN, Lang.RUSSIAN))
    grazie.runHighlightTestForFile("Docs.py")
  }

  @Test
  fun `test grammar check in comments`() {
    grazie.runHighlightTestForFile("Comments.py")
  }

  @Test
  fun `test grammar check in string literals`() {
    grazie.runHighlightTestForFile("StringLiterals.py")
  }

  // PY-53047
  @Test
  fun `test docstring tags are excluded`() {
    GrazieConfig.update { it.withDomainEnabledRules(TextStyleDomain.CodeDocumentation, GrazieTestFixture.enabledRules) }
    grazie.runHighlightTestForFile("DocstringTagsAreExcluded.py")
  }

  @TestFor(issues=["PY-27635", "PY-62267"])
  @Test
  fun `test sphinx roles are excluded`() {
    GrazieConfig.update { it.withDomainEnabledRules(TextStyleDomain.CodeDocumentation, GrazieTestFixture.enabledRules) }
    grazie.runHighlightTestForFile("SphinxRolesAreExcluded.py")
  }

  @TestFor(issues=["PY-27635"])
  @Test
  fun `test sphinx markup in comments is excluded`() {
    grazie.runHighlightTestForFile("SphinxInCommentsExcluded.py")
  }

  @TestFor(issues=["IJPL-257025"])
  @Test
  fun `test docstring parameter names are excluded`() {
    grazie.runHighlightTestForFile("DocstringParameterNamesAreExcluded.py")
  }
}
