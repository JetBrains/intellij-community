/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.jetbrains.python

import com.intellij.psi.stubs.StubElement
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.fixtures.PyTestCase
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.psi.PyFile

/**
 * Stubs should be language level independent.
 * This test checks the cases when stubs can differ for different language levels.
 *
 * @author traff
 */
class PyUnifiedStubsTest : PyTestCase() {
  override fun getTestDataPath() = PythonTestUtil.getTestDataPath()!!

  private fun doTest(function: (languageLevel: LanguageLevel, file: PyFile) -> Boolean) {
    for (level in LanguageLevel.SUPPORTED_LEVELS) {
      runWithLanguageLevel(level) {
        val file = getFile()

        val shouldContainErrors = function(level, file)

        if (shouldContainErrors) {
          assertTrue(PsiTreeUtil.hasErrorElements(file))
        }
      }
      tearDown() // re-init fixture to avoid using cached psi
      setUp()
    }
  }

  private fun getFile(): PyFile {
    val vf = myFixture.copyFileToProject("psi/unified/${getTestName(false)}.py")
    return myFixture.psiManager.findFile(vf) as PyFile
  }

  fun testExecPy3() {
    doTest { level, file ->
      assertEquals("Python $level", "exec", file.topLevelFunctions[0].name)
      level.isPython2
    }
  }

  fun testStarParameter() {
    doTest { level, file ->
      val functionStub = file.topLevelFunctions[0].stub
      val paramListStub: StubElement<*> = functionStub!!.childrenStubs[0]
      assertNotNull("Function should contain star argument stub", paramListStub.findChildStubByType(PyElementTypes.SINGLE_STAR_PARAMETER))
      level.isPython2
    }
  }

  fun testAnnotations() {
    doTest { level, file ->
      val functionStub = file.topLevelFunctions[0].stub
      assertNotNull("Function should contain annotation stub", functionStub!!.findChildStubByType(PyElementTypes.ANNOTATION))
      level.isPython2
    }
  }

  fun testPrintWithEndArgument() {
    doTest { level, file ->
      assertEquals("'end' argument shouldn't break tuple parsing for Python 2", 2, file.topLevelFunctions.size)
      level.isPython2
    }
  }

  fun testExecAsArgument() {
    doTest { level, file ->
      assertEquals("'exec' used as an argument shouldn't break parser for Python 2", 2, file.topLevelFunctions.size)
      level.isPython2
    }
  }

  fun testAsyncDefWithDecorator() {
    doTest { level, file ->
      assertEquals("Async keyword shouldn't break function parsing for Python <3.5", 1, file.topLevelClasses[0].methods.size)
      level.isOlderThan(LanguageLevel.PYTHON35)
    }
  }
}