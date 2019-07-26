// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.env

import com.intellij.openapi.projectRoots.Sdk
import com.jetbrains.python.PythonFileType
import com.jetbrains.python.inspections.PyPep8Inspection
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

  private class Pep8Task(private val text: String) : PyExecutionFixtureTestTask(null) {

    override fun runTestOn(sdkHome: String, existingSdk: Sdk?) {
      myFixture.configureByText(PythonFileType.INSTANCE, text)
      myFixture.enableInspections(PyPep8Inspection::class.java)
      myFixture.checkHighlighting()
    }
  }
}