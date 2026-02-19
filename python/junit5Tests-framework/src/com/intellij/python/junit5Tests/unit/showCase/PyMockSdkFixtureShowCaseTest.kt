// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit.showCase

import com.intellij.python.junit5Tests.framework.env.pyMockSdkFixture
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.jetbrains.python.sdk.PythonSdkUtil
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

@TestApplication
class PyMockSdkFixtureShowCaseTest {
  private val pathFixture = tempPathFixture()
  private val sdkFixture = projectFixture().pyMockSdkFixture(pathFixture)

  @Test
  fun testMockSdk(): Unit = timeoutRunBlocking {
      Assertions.assertTrue(PythonSdkUtil.isPythonSdk(sdkFixture.get()), "SDK creation failed")
  }
}