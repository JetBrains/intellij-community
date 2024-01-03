// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.env

import com.intellij.testFramework.ProjectRule
import com.jetbrains.env.python.PySDKRule
import com.jetbrains.getPythonBinaryPath
import com.jetbrains.python.sdk.sdkSeemsValid
import kotlinx.coroutines.test.runTest
import org.junit.Assert
import org.junit.Test
import kotlin.time.Duration.Companion.minutes

/**
 * Tests sdk flavor
 * * Extend this class
 * * Implement [sdkRule]
 * * Use [com.intellij.testFramework.RuleChain] with [projectRule], [sdkRule] e.t.c as you do in JUnit4 test
 */
abstract class PySdkFlavorTestBase {
  protected val projectRule = ProjectRule()

  protected abstract val sdkRule: PySDKRule


  @Test
  fun testValid(): Unit = runTest(timeout = 2.minutes) {
    sdkRule.sdk.getPythonBinaryPath(projectRule.project).getOrThrow()
    repeat(1000) {
      Assert.assertTrue(sdkRule.sdk.sdkSeemsValid)
    }
  }
}
