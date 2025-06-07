// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.quickFixes

import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.PyQuickFixTestCase
import com.jetbrains.python.inspections.PyCompatibilityInspection

class PyReplaceWithOldStyleUnionQuickFixTest: PyQuickFixTestCase() {
  // PY-44974
  fun testBitwiseOrUnionReplacedByOldSyntaxInReturn() {
    doQuickFixTest(PyCompatibilityInspection::class.java,
                   PyPsiBundle.message("QFIX.replace.with.old.union.style"))
  }

  // PY-44974
  fun testBitwiseOrUnionReplacedByOldSyntaxInReturnComplexType() {
    doQuickFixTest(PyCompatibilityInspection::class.java,
                   PyPsiBundle.message("QFIX.replace.with.old.union.style"))
  }

  // PY-44974
  fun testBitwiseOrUnionReplacedByOldSyntaxInIsInstance() {
    doQuickFixTest(PyCompatibilityInspection::class.java,
                   PyPsiBundle.message("QFIX.replace.with.old.union.style"))
  }

  // PY-44974
  fun testBitwiseOrUnionReplacedByOldSyntaxInIsSubclass() {
    doQuickFixTest(PyCompatibilityInspection::class.java,
                   PyPsiBundle.message("QFIX.replace.with.old.union.style"))
  }

  // PY-44974
  fun testBitwiseOrUnionReplacedByOldSyntaxIntNoneWithOptional() {
    doQuickFixTest(PyCompatibilityInspection::class.java,
                   PyPsiBundle.message("QFIX.replace.with.old.union.style"))
  }

  // PY-44974
  fun testBitwiseOrUnionReplacedByOldSyntaxNoneIntWithOptional() {
    doQuickFixTest(PyCompatibilityInspection::class.java,
                   PyPsiBundle.message("QFIX.replace.with.old.union.style"))
  }

  // PY-44974
  fun testBitwiseOrUnionReplacedByOldSyntaxIntNoneStrWithUnion() {
    doQuickFixTest(PyCompatibilityInspection::class.java,
                   PyPsiBundle.message("QFIX.replace.with.old.union.style"))
  }

  // PY-44974
  fun testBitwiseOrUnionReplacedByOldSyntaxIntStrNoneWithUnion() {
    doQuickFixTest(PyCompatibilityInspection::class.java,
                   PyPsiBundle.message("QFIX.replace.with.old.union.style"))
  }

  // PY-44974
  fun testBitwiseOrUnionReplacedByOldSyntaxParenthesizedUnions() {
    doQuickFixTest(PyCompatibilityInspection::class.java,
                   PyPsiBundle.message("QFIX.replace.with.old.union.style"))
  }
}