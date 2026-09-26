// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit

import com.intellij.openapi.util.NlsSafe
import com.jetbrains.python.PythonInfo
import com.jetbrains.python.Result
import com.jetbrains.python.TraceContext
import com.jetbrains.python.isFailure
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.sdk.configuration.CreateSdkInfo
import com.jetbrains.python.sdk.configuration.getSdkCreator
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

internal class ExistingEnvTest {
  private companion object {
    fun ExistingEnv(expectedTitle: @NlsSafe String) =
      CreateSdkInfo.ExistingEnv(PythonInfo(LanguageLevel.PYTHON31), "...") {
        Assertions.assertEquals(expectedTitle, currentCoroutineContext()[TraceContext]?.title)
        Result.localizedError("...")
      }
  }

  @Test
  fun testContext(): Unit = runBlocking {
    val title = "some project"
    val result = ExistingEnv(expectedTitle = title).getSdkCreator(title).createSdk()
    Assertions.assertTrue(result.isFailure)
  }
}
