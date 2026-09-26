// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.env

import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.common.timeoutRunBlocking
import com.jetbrains.env.python.PySDKRule
import com.jetbrains.python.sdk.pySdkAdditionalData
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
  fun testValid(): Unit =  timeoutRunBlocking(2.minutes) {
    val sdk = sdkRule.sdk
    sdk.getPythonBinaryPath(projectRule.project).getOrThrow()
    // The flavor's own verdict, which this class is about, and which caches the file check it makes. Asking the
    // interpreter instead would launch a process per repetition — see `PythonInterpreter.validate`.
    repeat(1000) {
      Assert.assertTrue(sdk.pySdkAdditionalData.flavorAndData.sdkSeemsValid(sdk, null))
    }
  }
}
