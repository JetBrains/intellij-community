// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk

import com.intellij.python.sdk.backend.PythonEnvironmentProvider
import com.intellij.testFramework.junit5.TestApplication
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * The registered [PythonEnvironmentProvider] ids, in order.
 *
 * The ids are a public contract, because the MCP `get_python_environment` tool reports them. Each one lives in the xml
 * of the module that owns the kind, so a test guards them. `system` claims any layout and must stay last.
 *
 * This test needs every python module loaded, so it cannot live beside the detector in `intellij.python.sdk.tests`.
 */
@Subsystems.Interpreters
@Layers.Functional
@TestApplication
internal class PythonEnvironmentProvidersTest {
  @Test
  fun providerIds() {
    assertEquals(listOf("venv", "conda", "system"), PythonEnvironmentProvider.EP_NAME.filterableLazySequence().map { it.id }.toList())
  }
}
