// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit.interpreter

import com.intellij.testFramework.junit5.RegistryKeyAppLevel
import com.intellij.testFramework.junit5.TestApplication
import com.jetbrains.python.sdk.PythonSdkType
import com.jetbrains.python.tools.sdkTools.PythonMockSdk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * In the interpreter library flow, the SDK combos do not offer the user to add a Python SDK, and they show a Python SDK
 * as broken. A module names its interpreter by its interpreter library entry, not by an SDK.
 */
@RegistryKeyAppLevel(key = "python.interpreter.library.entries", value = "true")
@TestApplication
internal class PythonSdkTypeCreationTest {
  @Test
  fun `the user cannot add a Python SDK`() {
    assertThat(PythonSdkType.getInstance().allowCreationByUser()).isFalse()
  }

  @Test
  fun `a Python SDK is broken`() {
    assertThat(PythonSdkType.getInstance().sdkHasValidPath(PythonMockSdk.create())).isFalse()
  }
}

/**
 * In the legacy flow too, the SDK combos do not offer the user to add a Python SDK: the SDK of a module or a project is
 * never a Python SDK. The interpreter UI of Python adds an interpreter.
 */
@RegistryKeyAppLevel(key = "python.interpreter.library.entries", value = "false")
@TestApplication
internal class PythonSdkTypeCreationLegacyFlowTest {
  @Test
  fun `the user cannot add a Python SDK`() {
    assertThat(PythonSdkType.getInstance().allowCreationByUser()).isFalse()
  }
}
