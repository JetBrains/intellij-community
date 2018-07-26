// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.testing.pyTestFixtures

import com.intellij.openapi.extensions.ExtensionPointName
import com.jetbrains.python.psi.PyFunction

interface PyTestFixtureSubjectDetectorExtension {
  companion object {
    val EP_NAME = ExtensionPointName.create<PyTestFixtureSubjectDetectorExtension>("Pythonid.pyTestFixtureSubjectDetector")
  }

  fun isSubjectForFixture(function: PyFunction): Boolean
}