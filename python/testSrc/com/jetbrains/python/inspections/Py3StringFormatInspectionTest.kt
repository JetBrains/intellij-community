// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections

import com.intellij.idea.TestFor
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

  @TestFor(issues = ["PY-51322"])
  fun `test int`() = doTestByText("""
    f'{1:<warning descr="Format code 's' not supported for 'int'">s</warning>}'
    """.trimIndent())

  @TestFor(issues = ["PY-51322"])
  fun `test str`() = doTestByText("""
    f'{'':<warning descr="Format code 'f' not supported for 'str'">f</warning>}'
    """.trimIndent())

  @TestFor(issues = ["PY-51322"])
  fun `test union`() = doTestByText("""
    a: int | str
    f'{a:<warning descr="Format code 'f' not supported for 'str'">f</warning>}'
    """.trimIndent())

  @TestFor(issues = ["PY-51322"])
  fun `test decimal`() = doTestByText("""
    from decimal import Decimal

    f'{Decimal():f}'
    f'{Decimal():<warning>x</warning>}'
    """.trimIndent())

  @TestFor(issues = ["PY-51322"])
  fun `test invalid format spec`() = doTestByText("""
    f"{1:<warning descr="Invalid format specifier ':'">:</warning>}"
    """.trimIndent())

  @TestFor(issues = ["PY-51322"])
  fun `test nested width spec`() = doTestByText("f'{1:{10}}'")

  @TestFor(issues = ["PY-51322"])
  fun `test nested width and type spec`() = doTestByText("f'{1:{10}d}'")

  @TestFor(issues = ["PY-51322"])
  fun `test type conversion plus format type`() = doTestByText("""
    f'{1!s:<warning descr="Format code 'f' not supported for 'str'">f</warning>}'
    """.trimIndent())

  @TestFor(issues = ["PY-51322"])
  fun `test width only`() = doTestByText("f'{1:>10}'")

  @TestFor(issues = ["PY-51322"])
  fun `test precision float`() = doTestByText("f'{1.0:.5f}'")

  @TestFor(issues = ["PY-51322"])
  fun `test zero pad int`() = doTestByText("f'{1:08d}'")

  @TestFor(issues = ["PY-51322"])
  fun `test fill align width`() = doTestByText("f'{1:_>10}'")

  @TestFor(issues = ["PY-51322"])
  fun `test thousands separator`() = doTestByText("f'{1000:,}'")

  @TestFor(issues = ["PY-51322"])
  fun `test grouping underscore`() = doTestByText("f'{1000:_}'")

  @TestFor(issues = ["PY-51322"])
  fun `test sign plus`() = doTestByText("f'{1:+}'")

  @TestFor(issues = ["PY-51322"])
  fun `test alternate hex`() = doTestByText("f'{255:#x}'")

  @TestFor(issues = ["PY-51322"])
  fun `test bare width`() = doTestByText("f'{1:10}'")

  @TestFor(issues = ["PY-51322"])
  fun `test sign on str`() = doTestByText("""
    f'{'':<warning descr="Format code '+' not supported for 'str'">+</warning>}'
    """.trimIndent())

  @TestFor(issues = ["PY-51322"])
  fun `test alternate form on str`() = doTestByText("""
    f'{'':<warning descr="Format code '#' not supported for 'str'">#</warning>}'
    """.trimIndent())

  @TestFor(issues = ["PY-51322"])
  fun `test thousands separator on str`() = doTestByText("""
    f'{'':<warning descr="Format code ',' not supported for 'str'">,</warning>}'
    """.trimIndent())

  @TestFor(issues = ["PY-51322"])
  fun `test zero padding on str`() = doTestByText("""
    f'{'':<warning descr="Format code '0' not supported for 'str'">0</warning>}'
    """.trimIndent())

  @TestFor(issues = ["PY-51322"])
  fun `test width on str`() = doTestByText("f'{'':10}'")

  @TestFor(issues = ["PY-51322"])
  fun `test precision on str`() = doTestByText("f'{'':.2}'")

  @TestFor(issues = ["PY-51322"])
  fun `test sign on decimal`() = doTestByText("""
    from decimal import Decimal

    f'{Decimal():+}'
    """.trimIndent())

  @TestFor(issues = ["PY-51322"])
  fun `test fraction with f`() = doTestByText("""
    from fractions import Fraction

    f'{Fraction():f}'
    """.trimIndent())

  @TestFor(issues = ["PY-51322"])
  fun `test fraction invalid`() = doTestByText("""
    from fractions import Fraction

    f'{Fraction():<warning>x</warning>}'
    """.trimIndent())

  @TestFor(issues = ["PY-51322"])
  fun `test untyped expression`() = doTestByText("f'{x:f}'")

  @TestFor(issues = ["PY-51322"])
  fun `test Any`() =  withNewAnyTypeEnabled { doTestByText("""
    from typing import Any
    
    x: Any
    f'{x:f}'
    
    xy: int | Any
    f'{xy:<warning descr="Format code 's' not supported for 'int'">s</warning>}'
    """.trimIndent()) }

  @TestFor(issues = ["PY-51322"])
  fun `test custom class with format`() = doTestByText("""
    class Money:
        def __format__(self, spec):
            return "$100"

    f'{Money():f}'
    """.trimIndent())

  @TestFor(issues = ["PY-51322"])
  fun `test subclass without format override`() = doTestByText("""
    class MyInt(int):
        pass

    f'{MyInt():<warning descr="Format code 's' not supported for 'MyInt'">s</warning>}'
    """.trimIndent())

  @TestFor(issues = ["PY-51322"])
  fun `test subclass with format override`() = doTestByText("""
    class WeirdInt(int):
        def __format__(self, spec):
            return "weird"

    f'{WeirdInt():s}'
    """.trimIndent())

  @TestFor(issues = ["PY-51322"])
  fun `test LiteralString`() = doTestByText("""
    from typing import LiteralString

    def foo(s: LiteralString) -> None:
        f'{s:s}'
        f'{s:<warning descr="Format code 'b' not supported for 'LiteralString'">b</warning>}'
    """.trimIndent())

  override fun getInspectionClass() = PyStringFormatInspection::class.java

  override fun getTestCaseDirectory() = TEST_DIRECTORY

  override fun isLowerCaseTestFile() = false

  companion object {
    const val TEST_DIRECTORY: String = "inspections/PyStringFormatInspection/"
  }
}
