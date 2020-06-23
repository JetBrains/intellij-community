// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.intentions

import com.intellij.psi.codeStyle.CommonCodeStyleSettings
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.PythonTestUtil
import com.jetbrains.python.formatter.PyCodeStyleSettings

class PyConvertImportIntentionTest : PyIntentionTestCase() {
  private val pythonCodeStyleSettings: PyCodeStyleSettings
    get() = codeStyleSettings.getCustomSettings(PyCodeStyleSettings::class.java)

  override fun getTestDataPath(): String = PythonTestUtil.getTestDataPath() + "/intentions/convertImport"

  // PY-37858
  fun testAbsoluteToRelative() = doTestWithMultipleFiles(PyPsiBundle.message("INTN.convert.absolute.to.relative"))

  // PY-37858
  fun testRelativeToAbsolute() = doTestWithMultipleFiles(PyPsiBundle.message("INTN.convert.relative.to.absolute"))

  // PY-37858
  fun testAbsoluteToRelativeInInitFile() = doTestWithMultipleFiles(PyPsiBundle.message("INTN.convert.absolute.to.relative"), "pkg/__init__")

  // PY-37858
  fun testRelativeToAbsoluteInInitFile() = doTestWithMultipleFiles(PyPsiBundle.message("INTN.convert.relative.to.absolute"), "pkg/__init__")

  // PY-37858
  fun testPreservingCodestyle() {
    pythonCodeStyleSettings.FROM_IMPORT_WRAPPING = CommonCodeStyleSettings.WRAP_ALWAYS
    pythonCodeStyleSettings.FROM_IMPORT_NEW_LINE_BEFORE_RIGHT_PARENTHESIS = true
    pythonCodeStyleSettings.FROM_IMPORT_NEW_LINE_AFTER_LEFT_PARENTHESIS = true
    pythonCodeStyleSettings.FROM_IMPORT_PARENTHESES_FORCE_IF_MULTILINE = true
    pythonCodeStyleSettings.FROM_IMPORT_TRAILING_COMMA_IF_MULTILINE = true

    doTestWithMultipleFiles(PyPsiBundle.message("INTN.convert.absolute.to.relative"))
  }

  // PY-37858
  fun testForcingCodestyleIfCurrentIsDifferent() {
    pythonCodeStyleSettings.FROM_IMPORT_WRAPPING = CommonCodeStyleSettings.WRAP_ALWAYS

    doTestWithMultipleFiles(PyPsiBundle.message("INTN.convert.absolute.to.relative"))
  }

  // PY-37858
  fun testAbsoluteToRelativeForNonOverlappingLocations() {
    val root = getTestName(true)
    myFixture.copyDirectoryToProject(root, "")
    val file = myFixture.configureByFile("pkg/test.py")

    val intentionActions = myFixture.filterAvailableIntentions(PyPsiBundle.message("INTN.convert.absolute.to.relative"))
    assertEmpty(intentionActions)
    assertSdkRootsNotParsed(file)
  }

  // PY-37858
  fun testNoConvertingStdLibToRelative() = doNegativeTest(PyPsiBundle.message("INTN.convert.absolute.to.relative"))


  private fun doTestWithMultipleFiles(hint: String, file: String = "one/two/test") {
    val root = getTestName(true)
    myFixture.copyDirectoryToProject(root, "")
    myFixture.configureByFile("$file.py")

    val intentionAction = myFixture.findSingleIntention(hint)
    assertNotNull(intentionAction)
    myFixture.launchAction(intentionAction)
    myFixture.checkResultByFile("$root/$file.after.py", true)
  }
}
