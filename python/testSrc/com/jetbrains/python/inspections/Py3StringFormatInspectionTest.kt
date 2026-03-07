// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections

import com.jetbrains.python.fixtures.PyInspectionTestCase

class Py3StringFormatInspectionTest : PyInspectionTestCase() {
  // PY-16938
  fun testByteString() = doTest()

  fun testIndexElementWithPackedReferenceExpr() = doTest()

  fun testPackedDictLiteralInsideDictLiteral() = doTest()

  fun testPackedDictCallInsideDictLiteral() = doTest()

  fun testPackedListInsideList() = doTest()

  fun testPackedTupleInsideList() = doTest()

  fun testPackedTupleInsideTuple() = doTest()

  fun testPackedListInsideTuple() = doTest()

  fun testPackedRefInsideList() = doTest()

  fun testPackedRefInsideTuple() = doTest()

  // PY-20599
  fun testPy3kAsciiFormatSpecifier() = doTest()

  override fun getInspectionClass() = PyStringFormatInspection::class.java

  override fun getTestCaseDirectory() = TEST_DIRECTORY

  override fun isLowerCaseTestFile() = false

  companion object {
    const val TEST_DIRECTORY: String = "inspections/PyStringFormatInspection/"
  }
}
