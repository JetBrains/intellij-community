// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections

import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems

import com.jetbrains.python.fixtures.PyInspectionTestCase

@Subsystems.Inspections
@Layers.Functional
class PyUnhashableInspectionTest : PyInspectionTestCase() {

  override fun getInspectionClass() = PyUnhashableInspection::class.java

  fun testFindDictKeys() = doTest()

  fun testFindSetElements() = doTest()

  fun testFindHashArgument() = doTest()

  fun testIdentifyUnhashable() = doTest()

  fun testIdentifyUnhashableDataclasses() = doTest()
}
