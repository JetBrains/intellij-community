// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.quickFixes

import com.intellij.testFramework.TestDataPath
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.PyQuickFixTestCase
import com.jetbrains.python.inspections.PyPatternInspection

@TestDataPath("\$CONTENT_ROOT/../testData/quickFixes/PyAddMatchArgsFixTest")
class PyAddMatchArgsFixTest : PyQuickFixTestCase() {
  fun testAddMatchArgsBasic() {
    doQuickFixTest(PyPatternInspection::class.java, PyPsiBundle.message("QFIX.add.match.args.to.class", "Point"))
  }

  fun testAddMatchArgsFilterAttributes() {
    doQuickFixTest(PyPatternInspection::class.java, PyPsiBundle.message("QFIX.add.match.args.to.class", "C"))
  }

  fun testAddMatchArgsNoInit() {
    doQuickFixTest(PyPatternInspection::class.java, PyPsiBundle.message("QFIX.add.match.args.to.class", "Empty"))
  }

  fun testAddMatchArgsOnlySelf() {
    doQuickFixTest(PyPatternInspection::class.java, PyPsiBundle.message("QFIX.add.match.args.to.class", "OnlySelf"))
  }
}
