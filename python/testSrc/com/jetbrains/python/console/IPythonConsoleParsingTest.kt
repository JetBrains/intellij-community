// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.console

import com.intellij.lang.LanguageASTFactory
import com.intellij.openapi.application.PathManager
import com.intellij.testFramework.ParsingTestCase
import com.jetbrains.python.*
import com.jetbrains.python.psi.impl.PythonASTFactory

/**
 * Test parsing that is specific for IPython console.
 *
 * For the details see:
 *  https://ipython.readthedocs.io/en/stable/interactive/python-ipython-diff.html
 */
class IPythonConsoleParsingTest : ParsingTestCase(
  "psi",
  "py",
  true,
  PyConsoleParsingDefinition(true)
) {

  override fun setUp() {
    super.setUp()
    application.registerService(PyLanguageFacade::class.java, PyLanguageFacadeImpl())
    registerExtensionPoint(PythonDialectsTokenSetContributor.EP_NAME, PythonDialectsTokenSetContributor::class.java)
    registerExtension(PythonDialectsTokenSetContributor.EP_NAME, PythonTokenSetContributor())
    addExplicitExtension(LanguageASTFactory.INSTANCE, PythonLanguage.getInstance(), PythonASTFactory())
  }

  override fun getTestDataPath() = "${PathManager.getHomePath()}/community/python/testData/console/ipython"

  fun testHelp1() = doTestInternal()

  fun testHelp2() = doTestInternal()

  fun testHelp3() = doTestInternal()

  fun testHelpObjectPrefix() = doTestInternal()

  fun testHelpObjectSuffix() = doTestInternal()

  fun testHelpObjectVerbosePrefix() = doTestInternal()

  fun testHelpObjectVerboseSuffix() = doTestInternal()

  fun testHelpWildcards() = doTestInternal()

  fun testHelpError() = doTestInternal(false)

  fun testShell1() = doTestInternal()

  fun testShell2() = doTestInternal()

  fun testShell3() = doTestInternal()

  fun testShell4() = doTestInternal()

  fun testShell5() = doTestInternal()

  fun testShell6() = doTestInternal()

  fun testShellAssignment1() = doTestInternal()

  fun testShellAssignment2() = doTestInternal()

  fun testShellExpansion() = doTestInternal()

  fun testShellError() = doTestInternal(false)

  fun testMagic1() = doTestInternal()

  fun testMagic2() = doTestInternal()

  fun testMagic3() = doTestInternal()

  fun testMagicAssignment() = doTestInternal()

  fun testMagicError() = doTestInternal(false)

  fun testMagicMultiline() = doTestInternal()

  private fun doTestInternal(ensureNoErrorElements: Boolean = true) = doTest(true, ensureNoErrorElements)

}
