// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections

import com.jetbrains.python.fixtures.PyInspectionTestCase

class PyPandasTruthValueIsAmbiguousInspectionTest : PyInspectionTestCase() {
  override fun getInspectionClass(): Class<out PyInspection?> {
    return PyPandasTruthValueIsAmbiguousInspection::class.java
  }

  fun testDataFrame() {
    myFixture.copyDirectoryToProject("inspections/PyPandasTruthValueIsAmbiguousInspection/pandas", "pandas")
    doTest()
  }

  fun testSeries() {
    myFixture.copyDirectoryToProject("inspections/PyPandasTruthValueIsAmbiguousInspection/pandas", "pandas")
    doTest()
  }
}