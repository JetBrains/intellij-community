// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.env.systemPython

import com.intellij.python.community.services.systemPython.SystemPythonService
import com.intellij.python.junit5Tests.framework.env.PyEnvTestCase
import com.intellij.testFramework.common.timeoutRunBlocking
import com.jetbrains.python.getOrThrow
import com.jetbrains.python.psi.LanguageLevel
import org.junit.jupiter.api.Test

@PyEnvTestCase
class Py27Test {
  @Test
  fun testPy27(): Unit = timeoutRunBlocking {
    val testEnvironments = SystemPythonService().findSystemPythons()
    val python27 = testEnvironments.firstOrNull { it.languageLevel == LanguageLevel.PYTHON27 } ?: error("No 2.7 found in $testEnvironments")
    SystemPythonService().registerSystemPython(python27.pythonBinary).getOrThrow()
  }
}