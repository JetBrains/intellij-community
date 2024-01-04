// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.env.python

import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.jetbrains.env.EnvTestTagsRequired
import com.jetbrains.env.PyEnvTestCase
import com.jetbrains.env.PyExecutionFixtureTestTask
import com.jetbrains.python.PythonFileType
import com.jetbrains.python.inspections.PyPep8Inspection
import com.jetbrains.python.sdk.pythonSdk
import org.junit.Test

@EnvTestTagsRequired(tags = ["-iron"])
class PyPep8Test : PyEnvTestCase() {

  @Test
  fun cyrillicComment() {
    runPythonTest(
      Pep8Task(
        "# коммент коммент коммент коммент коммент коммент коммент коммент коммент коммент коммент коммент коммент\n" +
        "<weak_warning descr=\"PEP 8: E501 line too long (145 > 120 characters)\"># коммент коммент коммент коммент коммент коммент коммент коммент коммент коммент коммент коммент коммент коммент коммент коммент коммент коммент</weak_warning>\n" +
        "x = 10\n" +
        "if x > 10:\n" +
        "    print('f')\n" +
        "else:\n" +
        "    print('a')\n" +
        "\n" +
        "# comment comment comment comment comment comment comment comment comment comment comment comment comment comment\n" +
        "<weak_warning descr=\"PEP 8: E501 line too long (145 > 120 characters)\"># comment comment comment comment comment comment comment comment comment comment comment comment comment comment comment comment comment comment</weak_warning>\n" +
        "y = 10\n" +
        "if x > 10:\n" +
        "    print('f')\n" +
        "else:\n" +
        "    print('a')\n"
      )
    )
  }

  @Test
  fun suppressingWarningsWithNoqaComments() {
    runPythonTest(
      Pep8Task("""
        def func(x, y):
            func (x , y)  # noqa
            func (x , y)  # noqa E211,E203
            func<weak_warning descr="PEP 8: E211 whitespace before '('"> </weak_warning>(x<weak_warning descr="PEP 8: E203 whitespace before ','"> </weak_warning>, y)  # noqa: E300 # unrelated code
            func<weak_warning descr="PEP 8: E211 whitespace before '('"> </weak_warning>(x , y)  # noqa: E203 # specific code
            func (x , y)  # noqa: E2 # common prefix
        
        
        
        def<weak_warning descr="PEP 8: E271 multiple spaces after keyword">   </weak_warning>func2():  # noqa: E303 # blanks lines error
            pass  # noqa # error on the comment itself  
      """.trimIndent())
    )
  }

  private class Pep8Task(private val text: String) : PyExecutionFixtureTestTask(null) {

    override fun runTestOn(sdkHome: String, existingSdk: Sdk?) {
      myFixture.module.pythonSdk = existingSdk
      myFixture.configureByText(PythonFileType.INSTANCE, text)
      myFixture.enableInspections(PyPep8Inspection::class.java)
      myFixture.checkHighlighting()
    }
  }
}
