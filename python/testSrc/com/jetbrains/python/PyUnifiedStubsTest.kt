/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python

import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.fixtures.PyTestCase
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PySingleStarParameter
import junit.framework.TestCase

/**
 * @author traff
 */

/**
 * Stubs should be language level independent.
 *
 * This test checks the cases when stubs can differ for different language levels.
 *
 */
class PyUnifiedStubsTest : PyTestCase() {

  override fun getTestDataPath(): String {
    return PythonTestUtil.getTestDataPath()
  }

  private fun doTest(function: (languageLevel: LanguageLevel, file: PyFile) -> Boolean) {
    for (level in LanguageLevel.SUPPORTED_LEVELS) {
      runWithLanguageLevel(level) {
        val file = getFile()

        val shouldContainErrors = function(level, file)

        if (shouldContainErrors) {
          TestCase.assertTrue(PsiTreeUtil.hasErrorElements(file))
        }
      }
      tearDown() // re-init fixture to avoid using cached psi
      setUp()
    }
  }

  private fun getFile(): PyFile {
    val vf = myFixture.copyFileToProject("psi/${getTestName(false)}.py")
    val file = myFixture.psiManager.findFile(vf) as PyFile
    return file
  }

  fun testExecPy3() {
    doTest { level, file ->
      TestCase.assertEquals("Python $level", "exec", file.topLevelFunctions[0].name)
      level.isOutdatedPython2
    }
  }

  fun testStarParameter() {
    doTest { level, file ->
      val functionStub = file.topLevelFunctions[0].stub
      TestCase.assertNotNull("Function should contain star argument stub",
                             functionStub!!.childrenStubs[0].findChildStubByType<PySingleStarParameter>(
                               PyElementTypes.SINGLE_STAR_PARAMETER))
      level.isOutdatedPython2
    }
  }

  fun testAnnotations() {
    doTest { level, file ->
      val functionStub = file.topLevelFunctions[0].stub
      TestCase.assertNotNull("Function should contain annotation stub", functionStub!!.findChildStubByType(PyElementTypes.ANNOTATION))
      level.isOutdatedPython2
    }
  }

  fun testPrintWithEndArgument() {
    doTest { level, file ->
      TestCase.assertEquals("'end' argument shouldn't break tuple parsing for Python 2", 2, file.topLevelFunctions.size)
      level.isOutdatedPython2
    }
  }

  fun testExecAsArgument() {
    doTest { level, file ->
      TestCase.assertEquals("'exec' used as an argument shouldn't break parser for Python 2", 2, file.topLevelFunctions.size)
      level.isOutdatedPython2
    }
  }

  fun testAsyncDefWithDecorator() {
    doTest { level, file ->
      TestCase.assertEquals("Async keyword shouldn't break function parsing for Python <3.5", 1, file.topLevelClasses[0].methods.size)
      level.isOlderThan(LanguageLevel.PYTHON35)
    }
  }

}