// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.testing.pyTestFixtures

import com.intellij.openapi.extensions.ExtensionPointName
import com.jetbrains.python.psi.PyFunction

fun PyFunction.isSubjectForFixture() =
  PyTestFixtureExtension.EP_NAME.extensions.find { it.isSubjectForFixture(this) } != null

fun PyFunction.isInjectableLikeFixture() =
  PyTestFixtureExtension.EP_NAME.extensions.find { it.isInjectableLikeFixture(this) } != null

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
  fun isInjectableLikeFixture(function: PyFunction): Boolean = false
}