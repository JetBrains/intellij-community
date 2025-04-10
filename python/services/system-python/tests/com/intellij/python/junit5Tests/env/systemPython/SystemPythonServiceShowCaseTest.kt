// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.env.systemPython

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.util.SystemInfo
import com.intellij.platform.eel.spawnProcess
import com.intellij.platform.eel.getOrThrow
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.eel.provider.utils.readWholeText
import com.intellij.python.community.impl.venv.createVenv
import com.intellij.python.community.services.systemPython.SystemPythonProvider
import com.intellij.python.community.services.systemPython.SystemPythonService
import com.intellij.python.community.services.systemPython.SystemPythonServiceImpl
import com.intellij.python.junit5Tests.assertFail
import com.intellij.python.junit5Tests.framework.env.PyEnvTestCase
import com.intellij.python.junit5Tests.framework.env.PythonBinaryPath
import com.intellij.python.junit5Tests.framework.winLockedFile.deleteCheckLocking
import com.intellij.python.junit5Tests.randomBinary
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.registerExtension
import com.jetbrains.python.getOrThrow
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor
import com.jetbrains.python.venvReader.VirtualEnvReader
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
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
import kotlin.time.Duration.Companion.minutes

@PyEnvTestCase
class SystemPythonServiceShowCaseTest {

  @Test
  fun testListPythons(): Unit = timeoutRunBlocking(10.minutes) {
    for (systemPython in SystemPythonService().findSystemPythons(forceRefresh = true)) {
      fileLogger().info("Python found: $systemPython")
      val eelApi = systemPython.pythonBinary.getEelDescriptor().upgrade()
      val process = eelApi.exec.spawnProcess(systemPython.pythonBinary.pathString, "--version").eelIt()
      val output = async {
        (if (systemPython.languageLevel.isPy3K) process.stdout else process.stderr).readWholeText().getOrThrow()
      }
      Assertions.assertEquals(0, process.exitCode.await(), "Wrong exit code")
      val versionString = PythonSdkFlavor.getLanguageLevelFromVersionStringStaticSafe(output.await())!!
      Assertions.assertEquals(systemPython.languageLevel, versionString, "Wrong version")
    }
  }

  @Test
  fun testCustomPythonRainyDay(): Unit = timeoutRunBlocking(10.minutes) {
    SystemPythonService().registerSystemPython(randomBinary).assertFail()
  }

  @Test
  fun testCustomPythonSunnyDay(@PythonBinaryPath python: Path, @TempDir venvPath: Path): Unit = timeoutRunBlocking(10.minutes) {
    createVenv(python, venvPath).getOrThrow()
    val python = VirtualEnvReader.Instance.findPythonInPythonRoot(venvPath) ?: error("no python in $venvPath")
    val newPython = SystemPythonService().registerSystemPython(python).orThrow()
    var allPythons = SystemPythonService().findSystemPythons()
    assertThat("No newly registered python returned", allPythons, hasItem(newPython))
    if (SystemInfo.isWindows) {
      deleteCheckLocking(python)
    }
    else {
      python.deleteExisting()
    }

    allPythons = SystemPythonService().findSystemPythons(forceRefresh = true)
    assertThat("Broken python returned", allPythons, not(hasItem(newPython)))

    if (SystemInfo.isWindows) {
      deleteCheckLocking(venvPath)
    }
  }

  @Test
  fun testRefresh(@TestDisposable disposable: Disposable): Unit = timeoutRunBlocking(10.minutes) {
    val mockProvider = mockk<SystemPythonProvider>()
    coEvery { mockProvider.findSystemPythons(any()) } returns Result.failure(java.lang.AssertionError("..."))
    coEvery { mockProvider.uiCustomization } returns null
    val sut = SystemPythonService()
    sut.findSystemPythons()
    ApplicationManager.getApplication().registerExtension(SystemPythonProvider.EP, mockProvider, disposable)
    repeat(10) {
      sut.findSystemPythons()
    }
    coVerify(exactly = 0) { mockProvider.findSystemPythons(any()) }
    sut.findSystemPythons(forceRefresh = true)
    coVerify(atLeast = 1) { mockProvider.findSystemPythons(any()) }
  }

  @RegistryKey("python.system.refresh.minutes", "0")
  @Test
  fun testDisableCache(@TestDisposable disposable: Disposable): Unit = timeoutRunBlocking(10.minutes) {
    val timesToRepeat = 5
    val mockProvider = mockk<SystemPythonProvider>()
    coEvery { mockProvider.findSystemPythons(any()) } returns Result.success(emptySet())
    coEvery { mockProvider.uiCustomization } returns null
    val sut = SystemPythonServiceImpl(this)
    ApplicationManager.getApplication().registerExtension(SystemPythonProvider.EP, mockProvider, disposable)
    repeat(timesToRepeat) {
      sut.findSystemPythons()
    }
    coVerify(exactly = timesToRepeat) { mockProvider.findSystemPythons(any()) }
  }
}