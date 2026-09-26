// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.target

import com.intellij.remote.RemoteSdkException
import com.intellij.testFramework.junit5.TestApplication
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.sdk.flavors.PyFlavorAndData
import com.jetbrains.python.sdk.flavors.PyFlavorData
import com.jetbrains.python.sdk.flavors.UnixPythonSdkFlavor
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

@Subsystems.RemoteInterpreters
@Layers.Functional
@TestApplication
internal class PyInterpreterVersionUtilTest {
  /**
   * An SDK being set up records no interpreter path yet, and a version probe can reach it in that state. It used to
   * throw a `NullPointerException` from inside the probe, which `PythonSdkType.getVersionString` hid behind a broad
   * catch. The probe now fails the way it declares, so a caller can handle it.
   */
  @Test
  @DisplayName("a target that records no interpreter path fails as declared, not with a NullPointerException")
  fun noInterpreterPathThrowsRemoteSdkException(@TempDir workingDir: Path) {
    val data = PyTargetAwareAdditionalData(PyFlavorAndData(PyFlavorData.Empty, UnixPythonSdkFlavor.getInstance()), workingDir)

    assertThrows(RemoteSdkException::class.java) { data.getInterpreterVersionForJava() }
  }
}
