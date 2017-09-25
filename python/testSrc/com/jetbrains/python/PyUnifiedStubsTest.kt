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

import com.intellij.psi.PsiManager
import com.jetbrains.python.fixtures.PyTestCase
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.psi.PyFile
import junit.framework.TestCase

/**
 * @author traff
 */

class PyUnifiedStubsTest : PyTestCase() {

  override fun getTestDataPath(): String {
    return PythonTestUtil.getTestDataPath()
  }

  fun testExecPy3() {
    for (level in LanguageLevel.SUPPORTED_LEVELS) {
      runWithLanguageLevel(level) {
        val vf = myFixture.copyFileToProject("psi/${getTestName(false)}.py")
        val file = PsiManager.getInstance(myFixture.project).findFile(vf) as PyFile

        TestCase.assertEquals("Python $level", "exec", file.topLevelFunctions[0].name)
      }
    }
  }
}