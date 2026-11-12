// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.pythonPackageManager

import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.projectRoots.impl.ProjectJdkImpl
import com.intellij.python.sdk.backend.PySdkBundle
import com.intellij.python.sdk.backend.pythonInterpreterResultAsync
import com.intellij.python.venv.sdk.flavors.VirtualEnvSdkFlavor
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.jetbrains.python.Result
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.sdk.PythonSdkAdditionalData
import com.jetbrains.python.sdk.PythonSdkType
import com.jetbrains.python.sdk.flavors.PyFlavorAndData
import com.jetbrains.python.sdk.flavors.PyFlavorData
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

/**
 * A Python SDK with no SDK data names no interpreter, so no package manager can open for it. The IDE treats such an SDK
 * as no SDK: the interpreter lookup fails with the message that asks the user to recreate the SDK.
 */
@Subsystems.PackagingRequirements
@Layers.Functional
@TestApplication
internal class PythonPackageManagerNullAdditionalDataTest {
  @Test
  fun `an SDK with null additional data names no interpreter`(@TempDir home: Path): Unit = timeoutRunBlocking {
    val sdk = pythonSdk(home, data = null)

    assertBrokenConfiguration(sdk.pythonInterpreterResultAsync(), sdk.name)
  }

  @Test
  fun `an SDK with no home path names no interpreter`(@TempDir home: Path): Unit = timeoutRunBlocking {
    val sdk = pythonSdk(homePath = null, data = venvData(home))

    assertBrokenConfiguration(sdk.pythonInterpreterResultAsync(), sdk.name)
  }

  @Test
  fun `an SDK with a home path and data names an interpreter`(@TempDir home: Path): Unit = timeoutRunBlocking {
    val sdk = pythonSdk(home, venvData(home))

    val interpreter = sdk.pythonInterpreterResultAsync().orThrow()

    assertThat(interpreter.ownerDir).isEqualTo(home)
  }

  private suspend fun pythonSdk(home: Path, data: PythonSdkAdditionalData?): ProjectJdkImpl =
    pythonSdk(home.resolve("bin").resolve("python").toString(), data)

  private suspend fun pythonSdk(homePath: String?, data: PythonSdkAdditionalData?): ProjectJdkImpl {
    val sdk = ProjectJdkImpl(SDK_NAME, PythonSdkType.getInstance())
    // A commit of an SDK modificator needs the write lock, also for an SDK outside the SDK table.
    edtWriteAction {
      val modificator = sdk.sdkModificator
      modificator.homePath = homePath
      modificator.sdkAdditionalData = data
      modificator.commitChanges()
    }
    return sdk
  }

  private fun venvData(home: Path): PythonSdkAdditionalData =
    PythonSdkAdditionalData(PyFlavorAndData(PyFlavorData.Empty, VirtualEnvSdkFlavor.getInstance()), home)

  private fun assertBrokenConfiguration(result: PyResult<*>, sdkName: String) {
    assertThat(result).isInstanceOf(Result.Failure::class.java)
    val error = (result as Result.Failure).error
    assertThat(error.message).isEqualTo(PySdkBundle.message("python.sdk.broken.configuration", sdkName))
  }

  private companion object {
    const val SDK_NAME = "Python Null Additional Data"
  }
}
