// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.env.venv.showCase

import com.intellij.openapi.projectRoots.Sdk
import com.intellij.python.community.execService.Args
import com.intellij.python.community.execService.ExecService
import com.intellij.python.junit5Tests.framework.env.PyEnvTestCase
import com.intellij.python.junit5Tests.framework.env.PythonBinaryPath
import com.intellij.python.sdk.backend.getSdkAPI
import com.intellij.python.sdk.backend.pythonInterpreterResultAsync
import com.intellij.python.venv.createVenv
import com.intellij.python.venv.createVenvAdditionalData
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.getOrThrow
import com.jetbrains.python.isSuccess
import com.jetbrains.python.sdk.ModuleOrProject
import com.jetbrains.python.sdk.add.v2.PathHolder
import com.jetbrains.python.sdk.createSdk
import com.jetbrains.python.sdk.execGetStdout
import com.jetbrains.python.venvReader.Directory
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import kotlin.io.path.deleteExisting
import kotlin.time.Duration.Companion.minutes

@PyEnvTestCase
internal class PyVenvCreationManuallyShowCaseTest {
  private val projectFixture = projectFixture()

  @Test
  fun createVenvTest(@PythonBinaryPath python: PythonBinary, @TempDir venvDir: Directory): Unit = timeoutRunBlocking(5.minutes) {
    val venvPython = createVenv(python, venvDir).getOrThrow()
    val additionalData = createVenvAdditionalData(venvDir.parent)
    val interpreter = createSdk(ModuleOrProject.ProjectOnly(projectFixture.get()), PathHolder.Eel(venvPython), additionalData).getOrThrow()
    val sdk = interpreter.getSdkAPI()
    assertTrue(sdkIsValid(sdk),
               "Sdk not valid after creation")
    venvPython.deleteExisting()
    assertFalse(sdkIsValid(sdk),
                "Broken SDK is valid?")

    createVenv(python, venvDir).getOrThrow()
    assertTrue(sdkIsValid(sdk),
               "Sdk not valid after creation")

  }
}

private suspend fun sdkIsValid(sdk: Sdk): Boolean {
  val interpreter = sdk.pythonInterpreterResultAsync(forceRefresh = true).successOrNull ?: return false
  return ExecService().execGetStdout(interpreter, Args("-c", "print(1)")).isSuccess
}