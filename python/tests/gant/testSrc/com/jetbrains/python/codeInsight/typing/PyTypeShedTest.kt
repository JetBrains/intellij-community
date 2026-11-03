// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight.typing

import com.intellij.idea.TestFor
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import com.jetbrains.python.psi.LanguageLevel
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@Subsystems.CodeInsight
@Layers.Functional
@TestFor(issues = ["PY-83660"])
class PyTypeShedTest : PyCodeInsightTestCase() {

  @Test
  fun `stdlib module names hold the modules and packages of the language level`() {
    val names = PyTypeShed.findStdlibModuleNames(LanguageLevel.PYTHON310)
    assertThat(names).contains("builtins", "math", "_socket", "collections")
    assertThat(names).doesNotContain("tomllib", "VERSIONS", "@python2", "@tests")
  }

  @Test
  fun `stdlib module names hold a module from its first language level`() {
    assertThat(PyTypeShed.findStdlibModuleNames(LanguageLevel.PYTHON311)).contains("tomllib")
  }
}
