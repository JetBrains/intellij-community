// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python

import com.jetbrains.python.PythonHelpersLocator.Companion.getPythonCommunityPath
import com.jetbrains.python.fixtures.PyTestCase
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract


val testDataPath: String
  get() = "${getPythonCommunityPath()}/testData"

@OptIn(ExperimentalContracts::class)
inline fun <reified Clazz> PyTestCase.assertInstanceOf(obj: Any?) {
  contract {
    returns() implies (obj is Clazz?)
  }
  PyTestCase.assertInstanceOf(obj, Clazz::class.java)
}
