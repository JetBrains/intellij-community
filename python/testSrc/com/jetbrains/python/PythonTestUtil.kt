// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python

import com.intellij.python.community.helpersLocator.PythonHelpersLocator.Companion.getPythonCommunityPath
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
