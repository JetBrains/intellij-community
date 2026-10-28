// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk

import com.intellij.openapi.application.readAction
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.python.junit5Tests.framework.pyProjectFixture
import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.python.sdk.backend.PythonInterpreterRegistry
import com.intellij.python.sdk.backend.PythonInterpreterDetails
import com.intellij.python.sdk.backend.classRoots
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.RegistryKeyAppLevel
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.tools.sdkTools.PythonMockSdk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Files

/**
 * In the interpreter library flow, the updater stores the class roots of an interpreter in [PythonInterpreterDetails],
 * and [PythonInterpreter.classRoots] reads them there. It also writes them into the SDK, which lives in memory only.
 */
@RegistryKeyAppLevel(key = "python.interpreter.library.entries", value = "true")
@TestApplication
@Subsystems.Interpreters
@Layers.Functional
internal class PythonInterpreterDetailsTest {
  private val projectFixture = projectFixture()
  private val pyProjectFixture = projectFixture.pyProjectFixture()
  private val tempDir = tempPathFixture()

  @Test
  fun `an interpreter without stored roots reads the roots of its SDK`(): Unit = timeoutRunBlocking {
    withMockInterpreter { interpreter ->
      val project = projectFixture.get()
      assertThat(PythonInterpreterDetails.getInstance(project).hasClassRoots(interpreter)).isFalse()
      assertThat(interpreter.classRoots(project)).containsExactly(*sdkClassRoots(interpreter))
    }
  }

  @Test
  fun `stored roots win over the roots of the SDK`(): Unit = timeoutRunBlocking {
    withMockInterpreter { interpreter ->
      val project = projectFixture.get()
      val stored = listOf(newDir("stored"))
      assertThat(PythonInterpreterDetails.getInstance(project).setClassRoots(interpreter, stored)).isTrue()
      assertThat(interpreter.classRoots(project)).containsExactly(*stored.toTypedArray())

      PythonInterpreterDetails.getInstance(project).clear(interpreter)
      assertThat(interpreter.classRoots(project)).containsExactly(*sdkClassRoots(interpreter))
    }
  }

  @Test
  fun `setting the same roots again reports no change`(): Unit = timeoutRunBlocking {
    withMockInterpreter { interpreter ->
      val details = PythonInterpreterDetails.getInstance(projectFixture.get())
      val stored = listOf(newDir("same"))
      val before = details.changeCountForTests
      assertThat(details.setClassRoots(interpreter, stored)).isTrue()
      assertThat(details.changeCountForTests).isEqualTo(before + 1)
      assertThat(details.setClassRoots(interpreter, stored)).isFalse()
      assertThat(details.changeCountForTests).isEqualTo(before + 1)
    }
  }

  @Test
  fun `the updater stores the roots and writes them into the SDK`(): Unit = timeoutRunBlocking {
    val onSysPath = newDir("on_sys_path")
    withMockInterpreter(sysPath = listOf(onSysPath.path)) { interpreter ->
      val project = projectFixture.get()

      assertThat(PythonSdkUpdater.updateVersionAndPathsSynchronouslyAndScheduleRemaining(interpreter, project)).isTrue()

      assertThat(PythonInterpreterDetails.getInstance(project).hasClassRoots(interpreter)).isTrue()
      assertThat(interpreter.classRoots(project)).contains(onSysPath)
      // The SDK gets them too, for the code that still reads the roots of an SDK.
      assertThat(sdkClassRoots(interpreter)).contains(onSysPath)
    }
  }

  @Test
  fun `the roots of an interpreter that the registry drops go with it`(): Unit = timeoutRunBlocking {
    val project = projectFixture.get()
    val pyProject = pyProjectFixture.get()
    val registry = PythonInterpreterRegistry.getInstance(project)
    val interpreter = registry.addMockPythonInterpreter(pyProject, PythonMockSdk.create())
    PythonInterpreterDetails.getInstance(project).setClassRoots(interpreter, listOf(newDir("dropped")))

    registry.removePythonInterpreter(pyProject, interpreter)

    assertThat(PythonInterpreterDetails.getInstance(project).hasClassRoots(interpreter)).isFalse()
  }

  /** Runs [body] with a mock interpreter whose mock `sys.path` is [sysPath], when it is given. */
  private suspend fun withMockInterpreter(sysPath: List<String>? = null, body: suspend (PythonInterpreter) -> Unit) {
    val pyProject = pyProjectFixture.get()
    val registry = PythonInterpreterRegistry.getInstance(projectFixture.get())
    val sdk = PythonMockSdk.create()
    sysPath?.let { sdk.putUserData(PythonSdkType.MOCK_SYS_PATH_KEY, it) }
    val interpreter = registry.addMockPythonInterpreter(pyProject, sdk)
    try {
      body(interpreter)
    }
    finally {
      registry.removePythonInterpreter(pyProject, interpreter)
    }
  }

  private suspend fun sdkClassRoots(interpreter: PythonInterpreter): Array<VirtualFile> =
    readAction { PythonInterpreterDetails.sdkClassRootsForTests(interpreter) }

  private fun newDir(name: String): VirtualFile {
    val dir = Files.createDirectories(tempDir.get().resolve(name))
    return checkNotNull(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(dir))
  }
}
