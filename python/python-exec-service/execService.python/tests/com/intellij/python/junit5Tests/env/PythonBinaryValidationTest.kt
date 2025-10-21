// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.env

import com.intellij.python.community.execService.python.validatePythonAndGetInfo
import com.intellij.python.junit5Tests.framework.env.PyEnvTestCase
import com.intellij.python.junit5Tests.framework.env.PythonBinaryPath
import com.intellij.python.junit5Tests.randomBinary
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.Result
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

@PyEnvTestCase
class PythonBinaryValidationTest {
  @Test
  fun sunnyDayTest(@PythonBinaryPath python: PythonBinary): Unit = runBlocking {
    val info = python.validatePythonAndGetInfo().orThrow()
    Assertions.assertNotNull(info, "Failed to get python info")
  }

  @Test
  fun rainyDayTest(): Unit = runBlocking {
    when (val r = randomBinary.validatePythonAndGetInfo()) {
      is Result.Success -> {
        Assertions.fail("${randomBinary} isn't a python, should fail, but got ${r.result}")
      }
      is Result.Failure -> {
        Assertions.assertTrue(r.error.message.isNotBlank(), "No error returned")
      }
    }
  }
}