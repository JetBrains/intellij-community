// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python

import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.fixtures.PyLightProjectDescriptor
import com.jetbrains.python.psi.LanguageLevel
import org.junit.Test
import org.junit.jupiter.api.Assertions

@Subsystems.TestRunner
@Layers.Functional
internal class PyLightProjectDescriptorTest {
  @Test
  fun testTwoDescriptorsLevel() {
    val d1 = PyLightProjectDescriptor(LanguageLevel.PYTHON27)
    val d2 = PyLightProjectDescriptor(LanguageLevel.PYTHON27)
    val d3 = PyLightProjectDescriptor(LanguageLevel.PYTHON312)
    Assertions.assertEquals(d1, d2, "Descriptors with same level expected to be equal")
    Assertions.assertNotEquals(d1, d3, "Descriptors with different level expected to be not equal")
  }


  @Test
  fun testTwoDescriptorsName() {
    val d1 = PyLightProjectDescriptor("foo")
    val d2 = PyLightProjectDescriptor("foo")
    val d3 = PyLightProjectDescriptor("bar")
    Assertions.assertEquals(d1, d2, "Descriptors with same name expected to be equal")
    Assertions.assertNotEquals(d1, d3, "Descriptors with different name expected to be not equal")
  }

  @Test
  fun testInheritorsAreNeverEqual() {
    val d1 = PyLightProjectDescriptor("foo")
    val d2 = object : PyLightProjectDescriptor("foo") {}
    Assertions.assertNotEquals(d1, d2, "Inheritor shouldn't be equal to its parent")
    Assertions.assertNotEquals(d2, d1, "Inheritor shouldn't be equal to its parent")
  }
}
