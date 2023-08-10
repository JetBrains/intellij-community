// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.quickFixes

import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.PyQuickFixTestCase
import com.jetbrains.python.inspections.PyCompatibilityInspection

class PyAddFromFutureImportAnnotationsQuickFixTest: PyQuickFixTestCase() {
  // PY-44974
  fun testNotSuggestedOnBitwiseOrUnionInIsInstance() {
    doNegativeTest(PyPsiBundle.message("QFIX.add.from.future.import.annotations"))
  }

  // PY-44974
  fun testNotSuggestedOnBitwiseOrUnionInIsSubclass() {
    doNegativeTest(PyPsiBundle.message("QFIX.add.from.future.import.annotations"))
  }

  // PY-44974
  fun testSuggestedOnBitwiseOrUnionInReturnAnnotation() {
    doQuickFixTest(PyCompatibilityInspection::class.java,
                   PyPsiBundle.message("QFIX.add.from.future.import.annotations"))
  }

  // PY-44974
  fun testSuggestedOnBitwiseOrUnionInVariableAnnotation() {
    doQuickFixTest(PyCompatibilityInspection::class.java, PyPsiBundle.message("QFIX.add.from.future.import.annotations"))
  }

  private fun doNegativeTest(hint: String) {
    super.doNegativeTest(PyCompatibilityInspection::class.java, hint)
  }
}