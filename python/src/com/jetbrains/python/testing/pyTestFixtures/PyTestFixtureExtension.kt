// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.testing.pyTestFixtures

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.module.Module
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.types.TypeEvalContext

internal fun getCustomFixtures(context: TypeEvalContext, forWhat: PyFunction) =
  PyTestFixtureExtension.EP_NAME.extensions.map { it.getCustomFixtures(context, forWhat) }.flatten()

fun PyFunction.isSubjectForFixture() =
  PyTestFixtureExtension.EP_NAME.extensions.find { it.isSubjectForFixture(this) } != null

fun PyFunction.isCustomFixture() =
  PyTestFixtureExtension.EP_NAME.extensions.find { it.isCustomFixture(this) } != null

interface PyTestFixtureExtension {
  companion object {
    val EP_NAME = ExtensionPointName.create<PyTestFixtureExtension>("Pythonid.pyTestFixtureExtension")
  }

  /**
   * @return Boolean accepts fixtures
   */
  fun isSubjectForFixture(function: PyFunction): Boolean = false

  /**
   * @return Boolean could be injected like fixture itself
   */
  fun isCustomFixture(function: PyFunction): Boolean = false

  fun getCustomFixtures(context: TypeEvalContext, forWhat: PyFunction): List<PyTestFixture> = emptyList()
}