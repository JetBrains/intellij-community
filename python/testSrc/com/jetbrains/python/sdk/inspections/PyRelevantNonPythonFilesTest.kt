// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.inspections

import com.intellij.openapi.module.Module
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.disposableFixture
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.sdk.configuration.CreateSdkInfo
import com.jetbrains.python.sdk.configuration.PyProjectSdkConfigurationExtension
import com.jetbrains.python.sdk.configuration.PyProjectTomlConfigurationExtension
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private const val LATE_DEPENDENCY_FILE = "late-registered-tool.toml"

/**
 * [relevantNonPythonFiles] asks the configurator extensions which dependency files they own. It used to be a top-level
 * `val`, which answered from whenever the class happened to load: a read action cancelled during that initialization
 * left the class permanently unusable, and a configurator registered afterwards never showed up in the answer.
 */
@TestApplication
@Subsystems.Interpreters
@Layers.Functional
internal class PyRelevantNonPythonFilesTest {
  private val testDisposable by disposableFixture()

  @Test
  fun testConfiguratorRegisteredLateIsSeen() {
    assertFalse(LATE_DEPENDENCY_FILE in relevantNonPythonFiles(), "the configurator is not registered yet")

    val lateConfigurator = object : PyProjectSdkConfigurationExtension by PyProjectSdkConfigurationExtension.EP_NAME.extensionList.first() {
      override val potentialDependencyFiles: Set<String> = setOf(LATE_DEPENDENCY_FILE)

      override suspend fun checkEnvironmentAndPrepareSdkCreator(module: Module, venvsInModule: List<PythonBinary>): CreateSdkInfo? = null

      override fun asPyProjectTomlSdkConfigurationExtension(): PyProjectTomlConfigurationExtension? = null
    }
    PyProjectSdkConfigurationExtension.EP_NAME.point.registerExtension(lateConfigurator, testDisposable)

    assertTrue(LATE_DEPENDENCY_FILE in relevantNonPythonFiles(), "a configurator registered after the first call must still be seen")
  }
}
