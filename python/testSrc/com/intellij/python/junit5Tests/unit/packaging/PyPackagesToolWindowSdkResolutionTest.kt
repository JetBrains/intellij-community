// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit.packaging

import com.intellij.openapi.application.EDT
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.jetbrains.python.tools.sdkTools.PythonMockSdk
import com.jetbrains.python.PythonTestUtil
import com.intellij.python.junit5Tests.framework.pyModuleFixture
import com.jetbrains.python.junit5.framework.pyMockSdkFixture
import com.intellij.openapi.components.service
import com.intellij.python.pyproject.model.evolution.EvoPyProjectModel
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.sdk.PythonSdkType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.io.path.writeText

/**
 * Covers [EvoPyProjectModel.interpreterFor], the interpreter every Python surface shows for the file being edited —
 * the packages tool window and the interpreter widget alike, so the two never name different ones.
 *
 * Regression test for PY-91300: with an environment configured per subproject, the tool window used
 * to open on whichever module came first instead of the one owning the file in the editor.
 *
 * The two "selected file wins" cases are deliberately symmetric. Module order is not something the
 * test controls — the fixtures name modules after temp directories — so a single case could pass by
 * luck if its module happened to be scanned first. Under a first-module-wins regression exactly one
 * of the two must fail, whichever way the order falls.
 */
@TestApplication
internal class PyPackagesToolWindowSdkResolutionTest {
  private val projectFixture = projectFixture(openAfterCreation = true)

  private val firstModulePath = tempPathFixture()
  private val secondModulePath = tempPathFixture()
  // Python modules, so each is a `PyProject` the structure knows: that is what the resolution reads.
  private val firstModule = projectFixture.pyModuleFixture(firstModulePath, addPathToSourceRoot = true)
  private val secondModule = projectFixture.pyModuleFixture(secondModulePath, addPathToSourceRoot = true)

  // Distinct names matter: a module stores its SDK by name, so same-named mocks would make both
  // modules resolve to the same interpreter and the test would prove nothing.
  private val firstSdk = projectFixture.pyMockSdkFixture(firstModule) { mockPythonSdk("firstModuleSdk") }
  private val secondSdk = projectFixture.pyMockSdkFixture(secondModule) { mockPythonSdk("secondModuleSdk") }

  @Test
  fun `resolves the interpreter of the module owning the selected file`(): Unit = timeoutRunBlocking {
    val expected = bothInterpretersConfigured().second
    openFileIn(secondModulePath.get())

    assertSame(expected, resolvedInterpreter(),
               "The tool window must open on the interpreter of the subproject being edited")
  }

  @Test
  fun `resolves the interpreter of the other module when its file is selected`(): Unit = timeoutRunBlocking {
    val expected = bothInterpretersConfigured().first
    openFileIn(firstModulePath.get())

    assertSame(expected, resolvedInterpreter(),
               "The tool window must open on the interpreter of the subproject being edited")
  }

  @Test
  fun `resolves nothing when no file is open and the project root is not a Python project`(): Unit = timeoutRunBlocking {
    bothInterpretersConfigured()

    // Neither module is at the project root, so there is no main `PyProject` to answer for the project. The
    // interpreter widget shows nothing here, and both surfaces read the one answer.
    assertNull(resolvedInterpreter(),
               "With no editor to go by and no Python project at the root, neither surface can name an interpreter")
  }

  private suspend fun resolvedInterpreter(): Sdk? {
    val project = projectFixture.get()
    return project.service<EvoPyProjectModel>().interpreterFor(FileEditorManager.getInstance(project).selectedFiles.firstOrNull())
  }

  /**
   * Initializes both SDK fixtures. Fixtures are lazy, so without this the module that the test does
   * not name would have no interpreter at all and a first-module-wins regression would have nothing
   * to pick up.
   */
  private fun bothInterpretersConfigured(): Pair<Sdk, Sdk> = Pair(firstSdk.get(), secondSdk.get())

  private suspend fun openFileIn(moduleDir: Path) {
    val project: Project = projectFixture.get()
    val path = moduleDir.resolve("main.py").apply { writeText("") }
    val file = withContext(Dispatchers.IO) {
      VirtualFileManager.getInstance().refreshAndFindFileByNioPath(path) ?: error("$path is not in VFS")
    }
    withContext(Dispatchers.EDT) {
      FileEditorManager.getInstance(project).openFile(file, true)
    }
    // Guard the precondition: if the editor manager reports no selection the resolution silently
    // falls back to scanning modules, and the assertions below would stop meaning anything.
    assertTrue(withContext(Dispatchers.EDT) { FileEditorManager.getInstance(project).selectedFiles.isNotEmpty() },
               "Expected $path to be the selected file")
  }

  private fun mockPythonSdk(name: String): Sdk =
    PythonMockSdk.create(name, PythonTestUtil.getTestDataPath() + "/MockSdk", PythonSdkType.getInstance(), LanguageLevel.getLatest())
}
