// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.run

import com.intellij.idea.TestFor
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import org.jdom.Element
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@TestFor(classes = [AbstractPythonRunConfiguration::class])
@Subsystems.Run
@Layers.Functional
class PythonRunConfigurationInterpreterOptionsTest : PyCodeInsightTestCase() {
  @Test
  @TestFor(issues = ["PY-94298"])
  fun `a stored configuration without interpreter options reads empty options`() {
    val configuration = newConfiguration()
    configuration.interpreterOptions = "-u"

    configuration.readExternal(Element("configuration"))

    assertEquals("", configuration.interpreterOptions)
  }

  @Test
  @TestFor(issues = ["PY-94298"])
  fun `null interpreter options become empty options`() {
    val configuration = newConfiguration()

    configuration.setInterpreterOptions(null)

    assertEquals("", configuration.interpreterOptions)
  }

  private fun newConfiguration(): PythonRunConfiguration =
    PythonRunConfiguration.createRunConfigurationForTests(myFixture.project, PythonConfigurationType.getInstance().factory)
}
