// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections

import com.jetbrains.python.fixtures.PyTestCase

class PyMissingConstructorTest : PyTestCase() {
  fun testBasic() {
    doTest()
  }

  // PY-3278
  fun testQualifiedName() {
    doTest()
  }

  // PY-3238
  fun testNoConstructor() {
    doTest()
  }

  // PY-3313
  fun testDeepInheritance() {
    doTest()
  }

  // PY-3395
  fun testInheritFromSelf() {
    doTest()
  }

  // PY-4038
  fun testExplicitDunderClass() {
    doTest()
  }

  // PY-20038
  fun testImplicitDunderClass() {
    doTest()
  }

  // PY-7176
  fun testException() {
    doTest()
  }

  // PY-7699
  fun testInnerClass() {
    doTest()
  }

  fun testPy3k() {
    doTest()
  }

  // PY-33265
  fun testAbstractConstructor() {
    doTest()
  }

  private fun doTest() {
    myFixture.configureByFile(TEST_DIRECTORY + getTestName(true) + ".py")
    myFixture.enableInspections(PyMissingConstructorInspection::class.java)
    myFixture.checkHighlighting(true, false, false)
  }

  companion object {
    private const val TEST_DIRECTORY = "inspections/PyMissingConstructorInspection/"
  }
}
