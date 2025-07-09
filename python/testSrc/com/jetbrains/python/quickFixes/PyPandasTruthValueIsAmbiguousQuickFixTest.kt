// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.quickFixes

import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.PyQuickFixTestCase
import com.jetbrains.python.inspections.PyPandasTruthValueIsAmbiguousInspection

class PyPandasTruthValueIsAmbiguousQuickFixTest : PyQuickFixTestCase() {
  private val emptinessQuickFixName = PyPsiBundle.message("QFIX.pandas.truth.value.is.ambiguous.emptiness.check")
  private val noneQuickFixName = PyPsiBundle.message("QFIX.pandas.truth.value.is.ambiguous.none.check")

  @Throws(Exception::class)
  override fun setUp() {
    super.setUp()
    myFixture.copyDirectoryToProject("", "")
  }

  fun testEmptyCheck() {
    doQuickFixTest(PyPandasTruthValueIsAmbiguousInspection::class.java, emptinessQuickFixName)
  }

  fun testNotNone() {
    doQuickFixTest(PyPandasTruthValueIsAmbiguousInspection::class.java, noneQuickFixName)
  }


  fun testNegativeEmptyCheck() {
    doQuickFixTest(PyPandasTruthValueIsAmbiguousInspection::class.java, emptinessQuickFixName)
  }
}