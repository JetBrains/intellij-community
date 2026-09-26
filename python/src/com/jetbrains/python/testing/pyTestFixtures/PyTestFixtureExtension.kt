// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.testing.pyTestFixtures

import com.intellij.openapi.extensions.ExtensionPointName
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.types.TypeEvalContext
import org.jetbrains.annotations.ApiStatus

internal fun getCustomFixtures(context: TypeEvalContext, forWhat: PyFunction) =
  PyTestFixtureExtension.EP_NAME.extensions.map { it.getCustomFixtures(context, forWhat) }.flatten()

@ApiStatus.Internal
internal fun PyFunction.isSubjectForFixture() =
  PyTestFixtureExtension.EP_NAME.extensions.find { it.isSubjectForFixture(this) } != null

@ApiStatus.Internal
internal fun PyFunction.isCustomFixture() =
  PyTestFixtureExtension.EP_NAME.extensions.find { it.isCustomFixture(this) } != null

@ApiStatus.Internal
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