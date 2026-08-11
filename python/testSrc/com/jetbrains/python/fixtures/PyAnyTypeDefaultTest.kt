package com.jetbrains.python.fixtures

import com.intellij.openapi.util.registry.Registry
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems

/**
 * Verifies the default-on / opt-out wiring for the `python.type.any` engine in [PyTestCase]:
 * it is enabled by default for every test, and [PyAnyTypeDisabled] opts a single test out.
 */
@Subsystems.Typing
@Layers.Functional
class PyAnyTypeDefaultTest : PyTestCase() {

  fun testEnabledByDefault() {
    assertTrue("python.type.any must be enabled by default in PyTestCase tests",
               Registry.get("python.type.any").asBoolean())
  }

  @PyAnyTypeDisabled
  fun testDisabledByAnnotation() {
    assertFalse("@PyAnyTypeDisabled must opt the test out of python.type.any",
                Registry.get("python.type.any").asBoolean())
  }
}
