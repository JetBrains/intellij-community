// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.documentation

import com.intellij.idea.TestFor
import com.intellij.openapi.application.EDT
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.ReflectionUtil
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import kotlinx.coroutines.Dispatchers
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.awt.Component

@TestApplication
@TestFor(issues = ["PY-94296"])
@Subsystems.QuickDocumentation
@Layers.Functional
class PythonDocumentationConfigurableTest {
  @Test
  fun `the constructor creates no Swing component`() {
    val configurable = PythonDocumentationConfigurable()

    val components = ReflectionUtil.collectFields(PythonDocumentationConfigurable::class.java)
      .onEach { it.isAccessible = true }
      .mapNotNull { it.get(configurable) as? Component }
    assertThat(components)
      .describedAs("the settings dialog can construct a configurable off the EDT, so Swing components belong in createComponent")
      .isEmpty()
  }

  @Test
  fun `reset fills the table that createComponent creates`(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    val map = PythonDocumentationMap.getInstance()
    val savedEntries = map.entries
    val configurable = PythonDocumentationConfigurable()
    try {
      configurable.createComponent()
      configurable.reset()
      assertThat(configurable.isModified).isFalse()

      map.entries = savedEntries + ("mymodule" to "https://example.com/{module.name}")
      assertThat(configurable.isModified).isTrue()

      configurable.reset()
      assertThat(configurable.isModified).isFalse()

      configurable.apply()
      assertThat(map.entries).containsEntry("mymodule", "https://example.com/{module.name}")
    }
    finally {
      configurable.disposeUIResources()
      map.entries = savedEntries
    }
  }
}
