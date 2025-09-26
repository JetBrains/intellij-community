// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.quickFixes

import com.intellij.testFramework.TestDataPath
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.PyQuickFixTestCase
import com.jetbrains.python.inspections.PyPatternInspection

@TestDataPath($$"$CONTENT_ROOT/../testData/quickFixes/PyRemovePatternFixTest")
class PyRemovePatternFixTest : PyQuickFixTestCase() {
  fun testRemoveLastExtraPositional() {
    doQuickFixTest(PyPatternInspection::class.java, PyPsiBundle.message("QFIX.remove.pattern"))
  }

  fun testRemoveConflictingKeyword() {
    doQuickFixTest(PyPatternInspection::class.java, PyPsiBundle.message("QFIX.remove.pattern"))
  }

  fun testRemoveSingleExtraPositional() {
    doQuickFixTest(PyPatternInspection::class.java, PyPsiBundle.message("QFIX.remove.pattern"))
  }
}
