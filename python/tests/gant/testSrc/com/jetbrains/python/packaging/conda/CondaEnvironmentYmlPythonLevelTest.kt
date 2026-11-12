// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.conda

import com.intellij.python.community.impl.conda.environmentYml.format.CondaEnvironmentYmlParser
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.jetbrains.python.allure.Components
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.sdk.conda.condaSupportedLanguages
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

@TestApplication
@Subsystems.Interpreters
@Components.Conda
@Layers.Functional
internal class CondaEnvironmentYmlPythonLevelTest {
  @ParameterizedTest
  @CsvSource(
    delimiter = '|',
    value = [
      "python=3.11 | PYTHON311",
      "python==3.11 | PYTHON311",
      "python=3.11.4 | PYTHON311",
      "python=3.11.4=h955ad1f_0 | PYTHON311",
      "python=3.11.* | PYTHON311",
      "conda-forge::python=3.10 | PYTHON310",
      "python>=3.10,<3.12 | PYTHON311",
      "python<3.12 | PYTHON311",
    ],
  )
  fun `python dependency selects the language level`(dependency: String, expected: LanguageLevel) {
    assertThat(readLevel(dependency)).isEqualTo(expected)
  }

  @Test
  fun `python without a version selects the newest level`() {
    assertThat(readLevel("python")).isEqualTo(condaSupportedLanguages.max())
  }

  @ParameterizedTest
  @ValueSource(strings = ["scipy", "python=2.5", "python>=4.0"])
  fun `no python dependency or no supported level selects nothing`(dependency: String) {
    assertThat(readLevel(dependency)).isNull()
  }

  private fun readLevel(dependency: String): LanguageLevel? {
    val file = LightVirtualFile("environment.yml", """
      name: myenv1
      channels:
        - defaults
      dependencies:
        - $dependency
        - numpy
    """.trimIndent())
    return timeoutRunBlocking { CondaEnvironmentYmlParser.readPythonLanguageLevelFromFile(file, condaSupportedLanguages) }
  }
}
