// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.env.services.internal.impl

import com.intellij.python.community.services.internal.impl.VanillaPythonWithPythonInfoImpl
import com.intellij.python.junit5Tests.framework.env.PyEnvTestCase
import com.intellij.python.junit5Tests.framework.env.PythonBinaryPath
import com.intellij.python.junit5Tests.randomBinary
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.Result
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

@PyEnvTestCase
class PythonWithLanguageLevelImplTest {
  @Test
  fun testRainyDay(): Unit = runBlocking {
    when (val r = VanillaPythonWithPythonInfoImpl.createByPythonBinary(randomBinary)) {
      is Result.Failure -> Unit
      is Result.Success -> fail("Unexpected success ${r.result}")
    }
  }

  @Test
  fun testSunnyDay(@PythonBinaryPath pythonBinary: PythonBinary): Unit = runBlocking {
    val python = VanillaPythonWithPythonInfoImpl.createByPythonBinary(pythonBinary).orThrow()
    assertEquals(pythonBinary, python.pythonBinary, "Wrong python binary")
    assertTrue(python.pythonInfo.languageLevel.isPy3K, "Wrong python version")
  }
}