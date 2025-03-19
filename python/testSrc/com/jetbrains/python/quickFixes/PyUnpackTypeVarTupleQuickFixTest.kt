package com.jetbrains.python.quickFixes

import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.PyQuickFixTestCase
import com.jetbrains.python.inspections.PyTypeHintsInspection
import com.jetbrains.python.psi.LanguageLevel

class PyUnpackTypeVarTupleQuickFixTest : PyQuickFixTestCase() {
  // PY-53105
  fun testUnpackWithTypingExtensionUnpackOlderVersions() {
    doTest(LanguageLevel.PYTHON310)
  }

  // PY-53105
  fun testUnpackWithStarExpression() {
    doTest()
  }

  fun doTest(languageLevel: LanguageLevel = LanguageLevel.getLatest()) {
    runWithLanguageLevel(languageLevel) {
      val testFileName = getTestName(true)
      myFixture.enableInspections(PyTypeHintsInspection::class.java)
      myFixture.configureByFile("$testFileName.py")
      myFixture.checkHighlighting(false, false, false)
      val intentionAction = myFixture.getAvailableIntention(PyPsiBundle.message("QFIX.NAME.unpack.type.var.tuple"))
      assertNotNull(intentionAction)
      myFixture.launchAction(intentionAction!!)
      myFixture.checkResultByFile(testFileName + "_after.py", true)
    }
  }
}