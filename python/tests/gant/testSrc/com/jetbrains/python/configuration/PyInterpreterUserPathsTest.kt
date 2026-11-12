// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.configuration

import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.python.junit5Tests.framework.pyProjectFixture
import com.intellij.python.sdk.backend.PythonInterpreterDetails
import com.intellij.python.sdk.backend.PythonInterpreterRegistry
import com.intellij.python.sdk.backend.getSdkAPI
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
 * The paths dialog writes the paths that the user added and excluded as facts of the interpreter, see
 * [writeUserPaths]. In the interpreter library flow the record holds them, and the SDK holds no fact.
 */
@RegistryKeyAppLevel(key = "python.interpreter.library.entries", value = "true")
@TestApplication
@Subsystems.Interpreters
@Layers.Functional
internal class PyInterpreterUserPathsTest {
  private val projectFixture = projectFixture()
  private val pyProjectFixture = projectFixture.pyProjectFixture()
  private val tempDir = tempPathFixture()

  @Test
  fun `the dialog writes the user paths as facts`(): Unit = timeoutRunBlocking {
    val project = projectFixture.get()
    val pyProject = pyProjectFixture.get()
    val registry = PythonInterpreterRegistry.getInstance(project)
    val interpreter = registry.addMockPythonInterpreter(pyProject, PythonMockSdk.create())
    try {
      val added = newDir("added")
      val excluded = newDir("excluded")
      @Suppress("DEPRECATION") val sdk = interpreter.getSdkAPI()

      writeUserPaths(project, sdk, setOf(added), setOf(excluded))

      val facts = PythonInterpreterDetails.getInstance(project).facts(interpreter)
      assertThat(facts.addedPaths).containsExactly(added.toNioPath())
      assertThat(facts.excludedPaths).containsExactly(excluded.toNioPath())
    }
    finally {
      registry.removePythonInterpreter(pyProject, interpreter)
    }
  }

  private suspend fun newDir(name: String): VirtualFile {
    val dir = Files.createDirectories(tempDir.get().resolve(name))
    return checkNotNull(edtWriteAction { LocalFileSystem.getInstance().refreshAndFindFileByNioFile(dir) })
  }
}
