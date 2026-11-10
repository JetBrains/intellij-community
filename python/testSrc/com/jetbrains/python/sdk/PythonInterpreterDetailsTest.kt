// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk

import com.intellij.openapi.application.readAction
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.python.junit5Tests.framework.pyProjectFixture
import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.python.sdk.backend.PythonInterpreterDetails
import com.intellij.python.sdk.backend.PythonInterpreterRegistry
import com.intellij.python.sdk.backend.PythonInterpreterRoots
import com.intellij.python.sdk.backend.classRoots
import com.intellij.python.sdk.backend.getSdkAPI
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.RegistryKeyAppLevel
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.run.PythonCommandLineState
import com.jetbrains.python.tools.sdkTools.PythonMockSdk
import com.jetbrains.python.tools.sdkTools.PythonMockSdkKeys
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

/**
 * [PythonInterpreterDetails] derives the class roots of an interpreter from its facts. A writer gives a `sys.path`, and
 * [PythonInterpreter.classRoots] reads the roots that follow. The SDK gets them too, for the code that still reads the
 * roots of an SDK.
 */
@RegistryKeyAppLevel(key = "python.interpreter.library.entries", value = "true")
@TestApplication
@Subsystems.Interpreters
@Layers.Functional
internal class PythonInterpreterDetailsTest {
  private val projectFixture = projectFixture()
  private val pyProjectFixture = projectFixture.pyProjectFixture()
  private val tempDir = tempPathFixture()

  private val details get() = PythonInterpreterDetails.getInstance(projectFixture.get())

  @Test
  fun `the roots follow the sys path that a writer gives`(): Unit = timeoutRunBlocking {
    withMockInterpreter { interpreter ->
      val project = projectFixture.get()
      val first = newDir("first")
      val second = newDir("second")

      assertThat(details.writeSysPath(interpreter, listOf(first.path))).isTrue()
      awaitRoots()
      assertThat(interpreter.classRoots(project)).contains(first).doesNotContain(second)

      assertThat(details.writeSysPath(interpreter, listOf(second.path))).isTrue()
      awaitRoots()
      assertThat(interpreter.classRoots(project)).contains(second).doesNotContain(first)
      // The SDK gets them too, for the code that still reads the roots of an SDK.
      assertThat(sdkClassRoots(interpreter)).contains(second).doesNotContain(first)
    }
  }

  @Test
  fun `the same sys path again reports no change`(): Unit = timeoutRunBlocking {
    withMockInterpreter { interpreter ->
      val sysPath = listOf(newDir("same").path)
      assertThat(details.writeSysPath(interpreter, sysPath)).isTrue()
      awaitRoots()
      val roots = PythonInterpreterRoots.getInstance(projectFixture.get())
      val before = roots.changeCountForTests

      assertThat(details.writeSysPath(interpreter, sysPath)).isFalse()
      assertThat(roots.changeCountForTests).isEqualTo(before)
    }
  }

  @Test
  fun `a version that the probe could not read leaves the version alone`(): Unit = timeoutRunBlocking {
    withMockInterpreter { interpreter ->
      val version = details.version(interpreter)

      assertThat(details.writeVersion(interpreter, null)).isFalse()
      assertThat(details.version(interpreter)).isEqualTo(version)
    }
  }

  @Test
  fun `the updater writes the sys path`(): Unit = timeoutRunBlocking {
    val onSysPath = newDir("on_sys_path")
    withMockInterpreter(sysPath = listOf(onSysPath.path)) { interpreter ->
      val project = projectFixture.get()

      assertThat(updateInterpreterFacts(interpreter, project).successOrNull).isNotNull()

      assertThat(details.facts(interpreter).sysPath).contains(onSysPath.toNioPath())
      assertThat(interpreter.classRoots(project)).contains(onSysPath)
    }
  }

  @Test
  fun `a path that the user adds reaches the PYTHONPATH of a run`(): Unit = timeoutRunBlocking {
    withMockInterpreter { interpreter ->
      val added = newDir("added")

      assertThat(details.writeAddedPaths(interpreter, listOf(added.toNioPath()))).isTrue()
      awaitRoots()

      // A run reads the paths from the SDK, which gets a copy of them. The record holds the fact.
      @Suppress("DEPRECATION") val sdk = interpreter.getSdkAPI()
      assertThat(PythonCommandLineState.getAddedPaths(sdk).map { Path.of(it) }).contains(added.toNioPath())
      assertThat(details.facts(interpreter).addedPaths).containsExactly(added.toNioPath())
    }
  }

  /** Waits until the roots follow the facts. A writer does not wait for them, see [PythonInterpreterRoots.requestDerive]. */
  private suspend fun awaitRoots() = PythonInterpreterRoots.getInstance(projectFixture.get()).awaitCommittedForTests()

  /** Runs [body] with a mock interpreter whose mock `sys.path` is [sysPath], when it is given. */
  private suspend fun withMockInterpreter(sysPath: List<String>? = null, body: suspend (PythonInterpreter) -> Unit) {
    val pyProject = pyProjectFixture.get()
    val registry = PythonInterpreterRegistry.getInstance(projectFixture.get())
    val sdk = PythonMockSdk.create()
    sysPath?.let { sdk.putUserData(PythonMockSdkKeys.SYS_PATH, it) }
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
