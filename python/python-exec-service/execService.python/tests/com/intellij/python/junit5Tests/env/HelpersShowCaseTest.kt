// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.env

import com.intellij.platform.eel.provider.asNioPath
import com.intellij.platform.eel.provider.localEel
import com.intellij.platform.testFramework.junit5.eel.params.api.*
import com.intellij.python.community.execService.ExecService
import com.intellij.python.community.execService.python.executeHelper
import com.intellij.python.community.execService.python.validatePythonAndGetVersion
import com.intellij.python.community.helpersLocator.PythonHelpersLocator
import com.intellij.python.junit5Tests.framework.env.PyEnvTestCase
import com.intellij.python.junit5Tests.framework.env.PythonBinaryPath
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.getOrThrow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.condition.OS
import org.junit.jupiter.params.ParameterizedTest
import java.nio.file.Path
import kotlin.io.path.deleteExisting
import kotlin.io.path.name
import kotlin.io.path.writeText

@TestApplicationWithEel(osesMayNotHaveRemoteEels = [OS.WINDOWS, OS.MAC, OS.LINUX])
@PyEnvTestCase
class HelpersShowCaseTest() {

  @WslTest("ubuntu", mandatory = false)
  @DockerTest(image = "python:3.13.4", mandatory = false)
  @EelSource
  @ParameterizedTest
  fun testHelpersWinExample(
    eelHolder: EelHolder,
    @PythonBinaryPath localPython: PythonBinary,
  ): Unit = runBlocking {
    val eel = eelHolder.eel
    // We might not have local python, so we use one from tests
    // Unlike docker/wsl, we can't choose local environment
    val python: Path = if (eel == localEel) localPython else eel.exec.findExeFilesInPath("python3").first().asNioPath()

    val helper = PythonHelpersLocator.getCommunityHelpersRoot().resolve("file.py")
    val hello = "hello"
    try {
      withContext(Dispatchers.IO) {
        helper.writeText("""
          print("$hello")
        """.trimIndent())
      }

      val output = ExecService().executeHelper(python, helper.name, listOf("--version")).orThrow().trim()
      Assertions.assertEquals(hello, output, "wrong helper output")

      val langLevel = ExecService().validatePythonAndGetVersion(python).getOrThrow()
      Assertions.assertTrue(langLevel.isPy3K, "Wrong lang level:$langLevel")
    }
    finally {
      withContext(Dispatchers.IO) {
        helper.deleteExisting()
      }
    }
  }
}