// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.env.systemPython

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.ExecuteProcessException
import com.intellij.platform.eel.ThrowsChecked
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.eel.provider.toEelApi
import com.intellij.platform.eel.provider.utils.readWholeText
import com.intellij.platform.eel.spawnProcess
import com.intellij.python.community.services.systemPython.SystemPythonProvider
import com.intellij.python.community.services.systemPython.SystemPythonService
import com.intellij.python.community.services.systemPython.SystemPythonServiceImpl
import com.intellij.python.junit5Tests.assertFail
import com.intellij.python.junit5Tests.framework.env.PyEnvTestCase
import com.intellij.python.junit5Tests.framework.env.PythonBinaryPath
import com.intellij.python.junit5Tests.randomBinary
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.registerExtension
import com.jetbrains.python.PyToolUIInfo
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.MessageError
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor
import kotlinx.coroutines.async
import org.hamcrest.MatcherAssert
import org.hamcrest.Matchers
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import kotlin.io.path.pathString
import kotlin.time.Duration.Companion.minutes

@PyEnvTestCase
class SystemPythonServiceShowCaseTest {

  @ThrowsChecked(ExecuteProcessException::class)
  @Test
  fun testListPythons(): Unit = timeoutRunBlocking(10.minutes) {
    for (systemPython in SystemPythonService().findSystemPythons(forceRefresh = true)) {
      fileLogger().info("Python found: $systemPython")
      val eelApi = systemPython.pythonBinary.getEelDescriptor().toEelApi()
      val process = eelApi.exec.spawnProcess(systemPython.pythonBinary.pathString, "--version").eelIt()
      val output = async {
        (if (systemPython.pythonInfo.languageLevel.isPy3K) process.stdout else process.stderr).readWholeText()
      }
      Assertions.assertTrue(process.exitCode.await() == 0)
      val versionString = PythonSdkFlavor.getLanguageLevelFromVersionStringStaticSafe(output.await())!!
      Assertions.assertEquals(systemPython.pythonInfo.languageLevel, versionString, "Wrong version")
    }
  }

  @Test
  fun testCustomPythonRainyDay(): Unit = timeoutRunBlocking(10.minutes) {
    SystemPythonService().registerSystemPython(randomBinary).assertFail()
  }

  @Test
  fun testRegister(@PythonBinaryPath path: PythonBinary): Unit = timeoutRunBlocking(10.minutes) {
    val sut = SystemPythonService()
    repeat(10) {
      sut.registerSystemPython(path).orThrow()
    }
    val pythons = sut.findSystemPythons(forceRefresh = true).map { it.pythonBinary }
    MatcherAssert.assertThat("No registered python", pythons, Matchers.hasItem(path))
    Assertions.assertEquals(pythons.distinct().size, pythons.size, "Duplicates found")
  }

  @Test
  fun testRefresh(@TestDisposable disposable: Disposable): Unit = timeoutRunBlocking(10.minutes) {
    val provider = CountingTestProvider(Result.failure(MessageError("...")))
    val sut = SystemPythonService()
    // Warm up cache before registering test provider
    sut.findSystemPythons()
    ApplicationManager.getApplication().registerExtension(SystemPythonProvider.EP, provider, disposable)
    repeat(10) {
      sut.findSystemPythons()
    }
    Assertions.assertTrue(provider.calls == 0, "Provider should not be called while cache is valid")
    sut.findSystemPythons(forceRefresh = true)
    Assertions.assertTrue(provider.calls >= 1, "Provider should be called after force refresh")
  }

  @RegistryKey("python.system.refresh.minutes", "0")
  @Test
  fun testDisableCache(@TestDisposable disposable: Disposable): Unit = timeoutRunBlocking(10.minutes) {
    val timesToRepeat = 5
    val provider = CountingTestProvider(Result.success(emptySet()))
    val sut = SystemPythonServiceImpl(this)
    ApplicationManager.getApplication().registerExtension(SystemPythonProvider.EP, provider, disposable)
    repeat(timesToRepeat) {
      sut.findSystemPythons()
    }
    Assertions.assertTrue(provider.calls == timesToRepeat)
  }

  private class CountingTestProvider(
    private val result: PyResult<Set<PythonBinary>>,
    override val uiCustomization: PyToolUIInfo? = null,
  ) : SystemPythonProvider {
    var calls: Int = 0
    override suspend fun findSystemPythons(eelApi: EelApi): PyResult<Set<PythonBinary>> {
      calls++
      return result
    }
  }
}