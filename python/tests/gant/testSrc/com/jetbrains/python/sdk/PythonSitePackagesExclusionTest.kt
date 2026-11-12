// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk

import com.intellij.idea.TestFor
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.python.junit5Tests.framework.pyProjectFixture
import com.intellij.python.pyproject.model.evolution.PyInterpreterLibrarySync
import com.intellij.python.pyproject.model.evolution.setPythonInterpreter
import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.python.sdk.backend.PythonInterpreterRegistry
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.RegistryKeyAppLevel
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.sdk.internal.PYTHON_MODULE_ID
import com.jetbrains.python.tools.sdkTools.PythonMockSdk
import com.jetbrains.python.tools.sdkTools.PythonMockSdkKeys
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Files

/**
 * The project excludes the `site-packages` under a class root of an interpreter that it uses, see
 * `sitePackagesToExclude`. A class root covers its whole tree, so the stdlib root of a virtual environment covers the
 * packages of its base interpreter too.
 *
 * The subclasses run the checks in each flow, see `python.interpreter.library.entries`.
 */
@TestFor(issues = ["PY-93818"])
internal abstract class PythonSitePackagesExclusionTest {
  protected val projectFixture = projectFixture(openAfterCreation = true)
  private val pyProjectFixture = projectFixture.pyProjectFixture()
  protected val tempDir = tempPathFixture()

  @Test
  fun `the site-packages under the stdlib root of an interpreter is excluded`(): Unit = timeoutRunBlocking {
    val lib = newDir("lib")
    val packageFile = newFile(newDir(lib, "site-packages"), "pkg.py")
    withMockInterpreter(listOf(lib.path)) {
      assertThat(isExcluded(packageFile)).isTrue()
    }
  }

  @Test
  fun `a site-packages that the interpreter has as a class root is not excluded`(): Unit = timeoutRunBlocking {
    val lib = newDir("lib")
    val sitePackages = newDir(lib, "site-packages")
    val packageFile = newFile(sitePackages, "pkg.py")
    withMockInterpreter(listOf(lib.path, sitePackages.path)) {
      assertThat(isExcluded(packageFile)).isFalse()
      assertThat(isInLibrary(packageFile)).isTrue()
    }
  }

  /** Runs [body] with a mock interpreter of the Python project whose `sys.path` is [sysPath], and its roots in the index. */
  protected suspend fun withMockInterpreter(sysPath: List<String>, body: suspend (PythonInterpreter) -> Unit) {
    val pyProject = pyProjectFixture.get()
    val registry = PythonInterpreterRegistry.getInstance(projectFixture.get())
    val sdk = PythonMockSdk.create()
    sdk.putUserData(PythonMockSdkKeys.SYS_PATH, sysPath)
    val interpreter = registry.addMockPythonInterpreter(pyProject, sdk)
    pyProject.setPythonInterpreter(interpreter)
    try {
      assertThat(updateInterpreterFacts(interpreter, projectFixture.get()).successOrNull).isNotNull()
      PyInterpreterLibrarySync.getInstance(projectFixture.get()).syncForTests()
      IndexingTestUtil.suspendUntilIndexesAreReady(projectFixture.get())
      body(interpreter)
    }
    finally {
      pyProject.setPythonInterpreter(null)
      registry.removePythonInterpreter(pyProject, interpreter)
    }
  }

  protected suspend fun isExcluded(file: VirtualFile): Boolean =
    readAction { ProjectFileIndex.getInstance(projectFixture.get()).isExcluded(file) }

  protected suspend fun isInLibrary(file: VirtualFile): Boolean =
    readAction { ProjectFileIndex.getInstance(projectFixture.get()).isInLibrary(file) }

  protected fun newDir(name: String): VirtualFile =
    checkNotNull(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(Files.createDirectories(tempDir.get().resolve(name))))

  protected fun newDir(parent: VirtualFile, name: String): VirtualFile =
    checkNotNull(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(Files.createDirectories(parent.toNioPath().resolve(name))))

  protected fun newFile(parent: VirtualFile, name: String): VirtualFile =
    checkNotNull(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(Files.createFile(parent.toNioPath().resolve(name))))
}

/** [PythonSitePackagesExclusionTest] in the legacy flow, where an SDK of the SDK table names the interpreter. */
@RegistryKeyAppLevel(key = "python.interpreter.library.entries", value = "false")
@Subsystems.Interpreters
@Layers.Functional
internal class PythonSitePackagesExclusionLegacyFlowTest : PythonSitePackagesExclusionTest() {
  /**
   * An SDK of the table that no module refers to excludes nothing. A virtual environment of the base interpreter has the
   * base stdlib root, without its `site-packages`, as a class root. It hid the packages of the base interpreter that
   * the project uses.
   */
  @Test
  fun `an unused virtual environment does not hide the packages of the used interpreter`(
    @TestDisposable disposable: Disposable,
  ): Unit = timeoutRunBlocking {
    val lib = newDir("lib")
    val sitePackages = newDir(lib, "site-packages")
    val packageFile = newFile(sitePackages, "pkg.py")
    val venv = PythonMockSdk.create()
    edtWriteAction {
      val modificator = venv.sdkModificator
      modificator.name = "PY-93818 unused virtual environment"
      modificator.removeAllRoots()
      modificator.addRoot(lib, OrderRootType.CLASSES)
      modificator.commitChanges()
      ProjectJdkTable.getInstance().addJdk(venv, disposable)
    }
    withMockInterpreter(listOf(lib.path, sitePackages.path)) {
      assertThat(isExcluded(packageFile)).isFalse()
      assertThat(isInLibrary(packageFile)).isTrue()
    }
  }

  /**
   * Two used SDKs share the stdlib root, and only the second one has its `site-packages` as a class root. The exclusion
   * of the first SDK follows the use and the roots of the second one.
   */
  @Test
  fun `the exclusion of an SDK follows the class roots of another used SDK`(
    @TestDisposable disposable: Disposable,
  ): Unit = timeoutRunBlocking {
    val project = projectFixture.get()
    val lib = newDir("lib")
    val sitePackages = newDir(lib, "site-packages")
    val packageFile = newFile(sitePackages, "pkg.py")
    val other = PythonMockSdk.create()
    edtWriteAction {
      setClassRoots(other, "PY-93818 other used SDK", lib, sitePackages)
      ProjectJdkTable.getInstance().addJdk(other, disposable)
    }
    withMockInterpreter(listOf(lib.path)) {
      assertThat(isExcluded(packageFile)).isTrue()

      edtWriteAction {
        val module = ModuleManager.getInstance(project).newModule(tempDir.get().resolve("other.iml"), PYTHON_MODULE_ID)
        ModuleRootModificationUtil.setModuleSdk(module, other)
      }
      assertThat(isExcluded(packageFile)).isFalse()

      edtWriteAction { setClassRoots(other, other.name, lib) }
      assertThat(isExcluded(packageFile)).isTrue()
    }
  }

  private fun setClassRoots(sdk: Sdk, name: String, vararg roots: VirtualFile) {
    val modificator = sdk.sdkModificator
    modificator.name = name
    modificator.removeAllRoots()
    roots.forEach { modificator.addRoot(it, OrderRootType.CLASSES) }
    modificator.commitChanges()
  }
}

/** [PythonSitePackagesExclusionTest] in the interpreter library flow, where the interpreter library holds the exclusions. */
@RegistryKeyAppLevel(key = "python.interpreter.library.entries", value = "true")
@Subsystems.Interpreters
@Layers.Functional
internal class PythonSitePackagesExclusionLibraryFlowTest : PythonSitePackagesExclusionTest()
