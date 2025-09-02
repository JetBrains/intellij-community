// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections

import com.jetbrains.python.fixtures.PyInspectionTestCase

class PyiOverloadsInspectionTest : PyInspectionTestCase() {
  fun testNoImplementation() {
    doTest()
  }

  fun testOverriddenMethods() {
    doTest()
  }

  fun testFinalMethods() {
    doTest()
  }

  override fun getTestFilePath(): String {
    return "$testCaseDirectory${getTestName(isLowerCaseTestFile)}.pyi"
  }

  override fun getInspectionClass(): Class<out PyInspection> = PyOverloadsInspection::class.java
}