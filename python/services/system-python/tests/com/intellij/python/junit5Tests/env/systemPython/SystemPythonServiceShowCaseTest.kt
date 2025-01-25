// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.env.systemPython

import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.util.SystemInfo
import com.intellij.platform.eel.executeProcess
import com.intellij.platform.eel.getOrThrow
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.eel.provider.utils.readWholeText
import com.intellij.python.community.services.systemPython.SystemPythonService
import com.intellij.python.junit5Tests.assertFail
import com.intellij.python.junit5Tests.framework.env.PyEnvTestCase
import com.intellij.python.junit5Tests.framework.env.PythonBinaryPath
import com.intellij.python.junit5Tests.framework.winLockedFile.deleteCheckLocking
import com.intellij.python.junit5Tests.randomBinary
import com.intellij.testFramework.common.timeoutRunBlocking
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.sdk.VirtualEnvReader
import com.jetbrains.python.createVirtualenv
import kotlinx.coroutines.async
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.hasItem
import org.hamcrest.Matchers.not
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.deleteExisting
import kotlin.io.path.pathString

@PyEnvTestCase
class SystemPythonServiceShowCaseTest {

  @Test
  fun testListPythons(): Unit = timeoutRunBlocking {
    for (systemPython in SystemPythonService().findSystemPythons()) {
      fileLogger().info("Python found: $systemPython")
      val eelApi = systemPython.pythonBinary.getEelDescriptor().upgrade()
      val process = eelApi.exec.executeProcess(systemPython.pythonBinary.pathString, "--version").getOrThrow()
      val output = async {
        process.stdout.readWholeText().getOrThrow()
      }
      Assertions.assertEquals(0, process.exitCode.await(), "Wrong exit code")
      val versionString = output.await()
      Assertions.assertEquals(systemPython.languageLevel, LanguageLevel.fromPythonVersion(versionString), "Wrong version")
    }
  }

  @Test
  fun testCustomPythonRainyDay(): Unit = timeoutRunBlocking {
    SystemPythonService().registerSystemPython(randomBinary).assertFail()
  }

  @Test
  fun testCustomPythonSunnyDay(@PythonBinaryPath python: Path, @TempDir venvPath: Path): Unit = timeoutRunBlocking {
    createVirtualenv(python, venvPath, venvPath)
    val python = VirtualEnvReader.Instance.findPythonInPythonRoot(venvPath) ?: error("no python in $venvPath")
    val newPython = SystemPythonService().registerSystemPython(python).orThrow()
    var allPythons = SystemPythonService().findSystemPythons()
    assertThat("No newly registered python returned", allPythons, hasItem(newPython))
    python.deleteExisting()

    allPythons = SystemPythonService().findSystemPythons()
    assertThat("Broken python returned", allPythons, not(hasItem(newPython)))

    if (SystemInfo.isWindows) {
      deleteCheckLocking(venvPath)
    }
  }
}