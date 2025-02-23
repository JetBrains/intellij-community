// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.env.venv.showCase

import com.intellij.python.community.impl.venv.createVenv
import com.intellij.python.community.testFramework.testEnv.TypeVanillaPython3.createSdk
import com.intellij.python.junit5Tests.framework.env.PyEnvTestCase
import com.intellij.python.junit5Tests.framework.env.PythonBinaryPath
import com.intellij.testFramework.common.timeoutRunBlocking
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.getOrThrow
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor
import com.jetbrains.python.sdk.getOrCreateAdditionalData
import com.jetbrains.python.venvReader.Directory
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import kotlin.io.path.deleteExisting
import kotlin.time.Duration.Companion.minutes

@PyEnvTestCase
class PyVenvCreationManuallyShowCaseTest {
  @Test
  fun createVenvTest(@PythonBinaryPath python: PythonBinary, @TempDir venvDir: Directory): Unit = timeoutRunBlocking(5.minutes) {
    val venvPython = createVenv(python, venvDir).getOrThrow()
    val sdk = createSdk(venvPython)
    val flavorAndData = sdk.getOrCreateAdditionalData().flavorAndData
    assertTrue(flavorAndData.sdkSeemsValid(sdk, null),
               "Sdk not valid after creation")
    venvPython.deleteExisting()
    // Clear "sdk is good"
    PythonSdkFlavor.clearExecutablesCache()
    assertFalse(flavorAndData.sdkSeemsValid(sdk, null),
                "Broken SDK is valid?")

    // "sdk is bad" should be cleared automatically by createVenv
    createVenv(python, venvDir).getOrThrow()
    assertTrue(flavorAndData.sdkSeemsValid(sdk, null),
               "Sdk not valid after creation")

  }
}