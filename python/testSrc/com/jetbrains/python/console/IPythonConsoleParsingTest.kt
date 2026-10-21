// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.console

import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.allure.Layers

import com.intellij.openapi.application.PathManager
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.parsingFixture
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.junit.jupiter.api.Test

/**
 * Test parsing that is specific for IPython console.
 *
 * For the details see:
 *  https://ipython.readthedocs.io/en/stable/interactive/python-ipython-diff.html
 */
@Subsystems.PythonConsole
@Layers.Functional
@TestApplication
class IPythonConsoleParsingTest {
  companion object {
    private val projectFixture = projectFixture()
  }

  private val parsing by parsingFixture(
    projectFixture,
    "${PathManager.getHomePath()}/community/python/testData/console/ipython/psi",
    "py",
    true,
    PyConsoleParsingDefinition(true)
  )

  @Test
  fun testHelp1() = doTestInternal()

  @Test
  fun testHelp2() = doTestInternal()

  @Test
  fun testHelp3() = doTestInternal()

  @Test
  fun testHelpObjectPrefix() = doTestInternal()

  @Test
  fun testHelpObjectSuffix() = doTestInternal()

  @Test
  fun testHelpObjectVerbosePrefix() = doTestInternal()

  @Test
  fun testHelpObjectVerboseSuffix() = doTestInternal()

  @Test
  fun testHelpWildcards() = doTestInternal()

  @Test
  fun testHelpError() = doTestInternal(false)

  @Test
  fun testShell1() = doTestInternal()

  @Test
  fun testShell2() = doTestInternal()

  @Test
  fun testShell3() = doTestInternal()

  @Test
  fun testShell4() = doTestInternal()

  @Test
  fun testShell5() = doTestInternal()

  @Test
  fun testShell6() = doTestInternal()

  @Test
  fun testShellAssignment1() = doTestInternal()

  @Test
  fun testShellAssignment2() = doTestInternal()

  @Test
  fun testShellExpansion() = doTestInternal()

  @Test
  fun testShellError() = doTestInternal(false)

  @Test
  fun testMagic1() = doTestInternal()

  @Test
  fun testMagic2() = doTestInternal()

  @Test
  fun testMagic3() = doTestInternal()

  @Test
  fun testMagicAssignment() = doTestInternal()

  @Test
  fun testMagicError() = doTestInternal(false)

  @Test
  fun testMagicMultiline() = doTestInternal()

  private fun doTestInternal(ensureNoErrorElements: Boolean = true) = parsing.doTest(true, ensureNoErrorElements)

}
