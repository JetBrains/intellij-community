// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.validation

import com.intellij.idea.TestFor
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.psi.LanguageLevel
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

/**
 * The bundled `pycodestyle.py` is 2.14.0. It calls `keyword.issoftkeyword`, which Python adds in 3.9.
 * An older interpreter must get one of the two older copies, which guard that call with a version check.
 */
@Subsystems.Inspections
@Layers.Functional
@TestFor(classes = [Pep8ExternalAnnotator::class])
class Pep8HelperSelectionTest {

  @ParameterizedTest
  @EnumSource(value = LanguageLevel::class, names = ["PYTHON27", "PYTHON34", "PYTHON35"])
  fun `an interpreter older than 3 6 gets pycodestyle 2 8 0`(level: LanguageLevel) {
    assertSame(Pep8ExternalAnnotator.PYCODESTYLE_2_8_0_PY, Pep8ExternalAnnotator.selectPycodestyleHelper(level))
  }

  @ParameterizedTest
  @EnumSource(value = LanguageLevel::class, names = ["PYTHON36", "PYTHON37", "PYTHON38"])
  fun `an interpreter from 3 6 to 3 8 gets pycodestyle 2 10 0`(level: LanguageLevel) {
    assertSame(Pep8ExternalAnnotator.PYCODESTYLE_2_10_0_PY, Pep8ExternalAnnotator.selectPycodestyleHelper(level))
  }

  @ParameterizedTest
  @EnumSource(value = LanguageLevel::class, names = ["PYTHON39", "PYTHON312", "PYTHON313"])
  fun `an interpreter from 3 9 gets the newest pycodestyle`(level: LanguageLevel) {
    assertSame(Pep8ExternalAnnotator.PYCODESTYLE_PY, Pep8ExternalAnnotator.selectPycodestyleHelper(level))
  }
}
