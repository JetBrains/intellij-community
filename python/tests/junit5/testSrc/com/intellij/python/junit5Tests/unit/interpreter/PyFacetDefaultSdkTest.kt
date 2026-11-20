// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit.interpreter

import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.RegistryKeyAppLevel
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.disposableFixture
import com.jetbrains.python.facet.defaultPythonFacetSdk
import com.jetbrains.python.tools.sdkTools.PythonMockSdk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

/**
 * In the interpreter library flow, a new Python facet names no SDK. A Python SDK of the SDK table would otherwise become
 * the interpreter of the module on the next model pass, and replace the interpreter that the user sets.
 */
@RegistryKeyAppLevel(key = "python.interpreter.library.entries", value = "true")
@TestApplication
internal class PyFacetDefaultSdkTest {
  private val disposable by disposableFixture()

  @Test
  fun `a new facet names no SDK of the SDK table`(): Unit = timeoutRunBlocking(10.seconds) {
    val sdk = PythonMockSdk.create()
    edtWriteAction { ProjectJdkTable.getInstance().addJdk(sdk, disposable) }
    assertThat(defaultPythonFacetSdk()).isNull()
  }
}

/** In the legacy flow, a new Python facet takes the first Python SDK of the SDK table, as an older build does. */
@RegistryKeyAppLevel(key = "python.interpreter.library.entries", value = "false")
@TestApplication
internal class PyFacetDefaultSdkLegacyFlowTest {
  private val disposable by disposableFixture()

  @Test
  fun `a new facet takes the first Python SDK of the SDK table`(): Unit = timeoutRunBlocking(10.seconds) {
    val sdk = PythonMockSdk.create()
    edtWriteAction { ProjectJdkTable.getInstance().addJdk(sdk, disposable) }
    assertThat(defaultPythonFacetSdk()).isNotNull()
  }
}
