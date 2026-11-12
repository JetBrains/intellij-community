// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk

import com.intellij.idea.TestFor
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.python.junit5Tests.framework.pyProjectFixture
import com.intellij.python.pyproject.model.evolution.PyInterpreterLibrarySync
import com.intellij.python.pyproject.model.evolution.setPythonInterpreter
import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.python.sdk.backend.PythonInterpreterDetails
import com.intellij.python.sdk.backend.PythonInterpreterRegistry
import com.intellij.python.sdk.backend.PythonInterpreterRoots
import com.intellij.python.sdk.backend.classRoots
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.RegistryKeyAppLevel
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.tools.sdkTools.PythonMockSdk
import com.jetbrains.python.tools.sdkTools.PythonMockSdkKeys
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Files

/**
 * A path of `sys.path` inside the owner module of an interpreter is a source root of the module, and no class root of
 * the interpreter or of its SDK. An editable install, such as the one of `uv sync`, puts the project root on `sys.path`.
 * A class root there makes the module a library, and the editor opens its files in reader mode.
 *
 * The subclasses run the checks in each flow, see `python.interpreter.library.entries`.
 */
@TestFor(issues = ["PY-93817"])
internal abstract class PythonInterpreterModuleRootsTest {
  private val projectFixture = projectFixture(openAfterCreation = true)
  private val pyProjectFixture = projectFixture.pyProjectFixture()
  private val tempDir = tempPathFixture()

  @Test
  fun `the project root on sys path is no class root`(): Unit = timeoutRunBlocking {
    val projectRoot = projectRoot()
    val sitePackages = newDir("site-packages")
    withMockInterpreter(listOf(sitePackages.path, projectRoot.path)) { interpreter, sdk ->
      val project = projectFixture.get()

      assertThat(updateInterpreterFacts(interpreter, project).successOrNull).isNotNull()

      assertThat(interpreter.classRoots(project)).contains(sitePackages).doesNotContain(projectRoot)
      assertThat(sdkClassRoots(interpreter)).contains(sitePackages).doesNotContain(projectRoot)
      assertThat(getPathsToTransfer(sdk)).doesNotContain(projectRoot)
    }
  }

  @Test
  fun `a directory of the module on sys path is a source root and no class root`(): Unit = timeoutRunBlocking {
    val src = newDir(projectRoot(), "src")
    withMockInterpreter(listOf(src.path)) { interpreter, _ ->
      val project = projectFixture.get()

      assertThat(updateInterpreterFacts(interpreter, project).successOrNull).isNotNull()

      assertThat(PythonInterpreterRoots.getInstance(project).sourceRoots(interpreter)).containsExactly(src)
      assertThat(interpreter.classRoots(project)).doesNotContain(src)
      assertThat(sdkClassRoots(interpreter)).doesNotContain(src)
      // The legacy flow transfers the roots on the EDT, and the interpreter library flow gives them with its sync.
      withContext(Dispatchers.EDT) { }
      PyInterpreterLibrarySync.getInstance(project).syncForTests()
      assertThat(readAction { ModuleRootManager.getInstance(pyProjectFixture.get().residesOnModule).sourceRoots.toList() }).contains(src)
    }
  }

  /** A second update reads the same `sys.path` again, so the roots stay. */
  @Test
  fun `a second update keeps the roots`(): Unit = timeoutRunBlocking {
    val src = newDir(projectRoot(), "src")
    val sitePackages = newDir("site-packages")
    withMockInterpreter(listOf(sitePackages.path, src.path)) { interpreter, _ ->
      val project = projectFixture.get()
      assertThat(updateInterpreterFacts(interpreter, project).successOrNull).isNotNull()
      val classRoots = sdkClassRoots(interpreter).toList()

      assertThat(updateInterpreterFacts(interpreter, project).successOrNull).isNotNull()

      assertThat(sdkClassRoots(interpreter).toList()).isEqualTo(classRoots).contains(sitePackages).doesNotContain(src)
      assertThat(PythonInterpreterRoots.getInstance(project).sourceRoots(interpreter)).containsExactly(src)
    }
  }

  /** Runs [body] with a mock interpreter of the Python project and its SDK. The mock `sys.path` is [sysPath]. */
  private suspend fun withMockInterpreter(sysPath: List<String>, body: suspend (PythonInterpreter, Sdk) -> Unit) {
    val pyProject = pyProjectFixture.get()
    val registry = PythonInterpreterRegistry.getInstance(projectFixture.get())
    val sdk = PythonMockSdk.create()
    sdk.putUserData(PythonMockSdkKeys.SYS_PATH, sysPath)
    val interpreter = registry.addMockPythonInterpreter(pyProject, sdk)
    pyProject.setPythonInterpreter(interpreter)
    try {
      body(interpreter, sdk)
    }
    finally {
      pyProject.setPythonInterpreter(null)
      registry.removePythonInterpreter(pyProject, interpreter)
    }
  }

  private suspend fun sdkClassRoots(interpreter: PythonInterpreter): Array<VirtualFile> =
    readAction { PythonInterpreterDetails.sdkClassRootsForTests(interpreter) }

  private fun projectRoot(): VirtualFile =
    checkNotNull(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(pyProjectFixture.get().baseDir))

  private fun newDir(name: String): VirtualFile =
    checkNotNull(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(Files.createDirectories(tempDir.get().resolve(name))))

  private fun newDir(parent: VirtualFile, name: String): VirtualFile =
    checkNotNull(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(Files.createDirectories(parent.toNioPath().resolve(name))))
}

/** [PythonInterpreterModuleRootsTest] in the legacy flow, where the SDK of the SDK table keeps the user paths. */
@RegistryKeyAppLevel(key = "python.interpreter.library.entries", value = "false")
@Subsystems.Interpreters
@Layers.Functional
internal class PythonInterpreterModuleRootsLegacyFlowTest : PythonInterpreterModuleRootsTest()

/** [PythonInterpreterModuleRootsTest] in the interpreter library flow, where the record keeps the facts. */
@RegistryKeyAppLevel(key = "python.interpreter.library.entries", value = "true")
@Subsystems.Interpreters
@Layers.Functional
internal class PythonInterpreterModuleRootsLibraryFlowTest : PythonInterpreterModuleRootsTest()
