// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi

import org.assertj.core.api.JUnitSoftAssertions
import org.junit.Rule
import org.junit.Test

class LanguageLevelTest {
  @JvmField
  @Rule
  val softly = JUnitSoftAssertions()

  @Test
  fun `test basic python version parsing`(){
    softly
      .assertThat(LanguageLevel.fromPythonVersion("3.6"))
      .isEqualTo(LanguageLevel.PYTHON36)
    softly
      .assertThat(LanguageLevel.fromPythonVersion("3.7"))
      .isEqualTo(LanguageLevel.PYTHON37)
    softly
      .assertThat(LanguageLevel.fromPythonVersion("3.8"))
      .isEqualTo(LanguageLevel.PYTHON38)
    softly
      .assertThat(LanguageLevel.fromPythonVersion("3.9"))
      .isEqualTo(LanguageLevel.PYTHON39)
    softly
      .assertThat(LanguageLevel.fromPythonVersion("3.10"))
      .isEqualTo(LanguageLevel.PYTHON310)
  }
}