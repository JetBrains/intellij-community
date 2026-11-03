// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit.sdk

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.python.community.common.tools.ToolId
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.ui.EmptyIcon
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.sdk.add.v2.FileSystem
import com.jetbrains.python.sdk.add.v2.PythonAddInterpreterProvider
import com.jetbrains.python.sdk.add.v2.PythonAddInterpreterProviders
import com.jetbrains.python.sdk.add.v2.PythonEnvironmentManager
import com.jetbrains.python.sdk.add.v2.PythonInterpreterSelectionMethod
import com.jetbrains.python.sdk.add.v2.PythonInterpreterSelectionMode
import com.jetbrains.python.sdk.add.v2.PythonNewEnvironmentDialogNavigator
import com.jetbrains.python.sdk.add.v2.PythonSupportedEnvironmentManagers
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

@TestApplication
@Subsystems.Interpreters
@Layers.Functional
internal class PythonAddInterpreterProviderTest {
  @ParameterizedTest
  @CsvSource("CREATE_NEW, VIRTUALENV, false", "SELECT_EXISTING, PYTHON, false", "SELECT_EXISTING, PYTHON, true")
  fun `the dialog restores the selected built-in manager`(
    method: PythonInterpreterSelectionMethod, manager: PythonSupportedEnvironmentManagers, storedToolId: Boolean,
  ) {
    val graph = PropertyGraph()
    val navigator = PythonNewEnvironmentDialogNavigator().apply {
      selectionMode = graph.property(PythonInterpreterSelectionMode.CUSTOM)
      selectionMethod = graph.property(method)
      newEnvManager = graph.property(PythonSupportedEnvironmentManagers.VIRTUALENV)
      existingEnvManager = graph.property(PythonSupportedEnvironmentManagers.PYTHON)
      environmentManagers = {
        when (selectionMethod.get()) {
          PythonInterpreterSelectionMethod.CREATE_NEW -> listOf(PythonSupportedEnvironmentManagers.VIRTUALENV)
          PythonInterpreterSelectionMethod.SELECT_EXISTING -> listOf(PythonSupportedEnvironmentManagers.PYTHON)
        }
      }
    }
    val properties = PropertiesComponent.getInstance()
    val keys = listOf(PythonNewEnvironmentDialogNavigator.FAV_MODE, PythonNewEnvironmentDialogNavigator.FAV_METHOD,
                      PythonNewEnvironmentDialogNavigator.FAV_MANAGER)
    val previous = keys.associateWith(properties::getValue)
    try {
      navigator.saveLastState()
      if (storedToolId) properties.setValue(PythonNewEnvironmentDialogNavigator.FAV_MANAGER, manager.toolId.id)
      navigator.newEnvManager.set(PythonSupportedEnvironmentManagers.CONDA)
      navigator.existingEnvManager.set(PythonSupportedEnvironmentManagers.CONDA)

      navigator.restoreLastState(listOf(PythonInterpreterSelectionMode.CUSTOM))

      val restored = when (method) {
        PythonInterpreterSelectionMethod.CREATE_NEW -> navigator.newEnvManager.get()
        PythonInterpreterSelectionMethod.SELECT_EXISTING -> navigator.existingEnvManager.get()
      }
      assertThat(restored).isSameAs(manager)
    }
    finally {
      for ((key, value) in previous) properties.setValue(key, value)
    }
  }

  @Test
  fun `quick actions use the manager order`() {
    val providers = PythonAddInterpreterProviders(listOf(provider("second", 20), provider("first", 10)))

    assertThat(providers.quickActionProviders().map { it.manager.toolId.id }).containsExactly("first", "second")
  }

  @Test
  fun `duplicate manager ids are rejected`() {
    assertThatThrownBy {
      PythonAddInterpreterProviders(listOf(provider("duplicate", 10), provider("duplicate", 20)))
    }.isInstanceOf(IllegalArgumentException::class.java)
  }

  private fun provider(id: String, order: Int) = object : PythonAddInterpreterProvider {
    override val manager = object : PythonEnvironmentManager {
      override val toolId = ToolId(id)
      override val displayName = id
      override val icon = EmptyIcon.ICON_16
      override val order = order
      override val sshAutoUploadRequired = false

      override fun supports(fileSystem: FileSystem<*>): Boolean = true
    }

    override val quickActionName = id
  }
}
