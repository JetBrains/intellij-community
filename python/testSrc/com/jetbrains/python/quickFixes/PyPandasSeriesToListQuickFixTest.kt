// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.quickFixes

import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.PyQuickFixTestCase
import com.jetbrains.python.inspections.PyPandasSeriesToListInspection

class PyPandasSeriesToListQuickFixTest : PyQuickFixTestCase() {
  private val quickFixName = PyPsiBundle.message("QFIX.pandas.series.values.replace.with.tolist")

  @Throws(Exception::class)
  override fun setUp() {
    super.setUp()
    myFixture.copyDirectoryToProject("", "")
  }

  fun testDataframeGetitem() {
    doQuickFixTest(PyPandasSeriesToListInspection::class.java, quickFixName)
  }

  fun testDataframeGetattr() {
    doQuickFixTest(PyPandasSeriesToListInspection::class.java, quickFixName)
  }


  fun testSeriesSimple() {
    doQuickFixTest(PyPandasSeriesToListInspection::class.java, quickFixName)
  }
}