// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.resolve

import com.intellij.idea.TestFor
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.util.QualifiedName
import com.intellij.python.junit5Tests.framework.pyProjectFixture
import com.intellij.python.pyproject.model.evolution.PyInterpreterLibrarySync
import com.intellij.python.pyproject.model.evolution.setPythonInterpreter
import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.python.sdk.backend.PythonInterpreterRegistry
import com.intellij.python.sdk.backend.addMockPythonInterpreter
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.RegistryKeyAppLevel
import com.intellij.testFramework.junit5.fixture.pathInProjectFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.project.PyProject
import com.jetbrains.python.sdk.updateInterpreterFacts
import com.jetbrains.python.tools.sdkTools.PythonMockSdk
import com.jetbrains.python.tools.sdkTools.PythonMockSdkKeys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Files
import kotlin.io.path.Path

/**
 * A module resolves an import in its own interpreter, and not in the interpreter of a module that it depends on. A
 * module dependency gives the sources of the other module only. The `pyproject.toml` model writes such a dependency
 * for a path dependency, and each of the two modules can have its own environment.
 *
 * The module `app` depends on the module `lib`. Each module has its own interpreter, and only the interpreter of `lib`
 * has the package [LIB_ENV_PACKAGE].
 *
 * The subclasses run the checks in each flow, see `python.interpreter.library.entries`.
 */
@TestFor(issues = ["PY-92303"], classes = [RootVisitorHost::class])
internal abstract class PyDependencyModuleInterpreterResolveTest {
  private val projectFixture = projectFixture(openAfterCreation = true)
  private val app = projectFixture.pyProjectFixture(projectFixture.pathInProjectFixture(Path("app")))
  private val lib = projectFixture.pyProjectFixture(projectFixture.pathInProjectFixture(Path("lib")))
  private val tempDir = tempPathFixture()

  @Test
  fun `a module resolves a package of its own interpreter`(): Unit = timeoutRunBlocking {
    withInterpreterOfEachModule {
      assertThat(resolve(LIB_ENV_PACKAGE, lib.get())).isNotEmpty()
    }
  }

  @Test
  fun `a module resolves a source file of its dependency module`(): Unit = timeoutRunBlocking {
    withInterpreterOfEachModule {
      assertThat(resolve(LIB_SOURCE, app.get())).isNotEmpty()
    }
  }

  @Test
  fun `a module does not resolve a package of the interpreter of its dependency module`(): Unit = timeoutRunBlocking {
    withInterpreterOfEachModule {
      assertThat(resolve(LIB_ENV_PACKAGE, app.get()))
        .describedAs("the interpreter of `app` does not have the package, so the import fails when `app` runs")
        .isEmpty()
    }
  }

  /**
   * Gives `app` and `lib` a mock interpreter each, makes `app` depend on `lib`, and runs [body] with the roots in the
   * index.
   */
  private suspend fun withInterpreterOfEachModule(body: suspend () -> Unit) {
    val project = projectFixture.get()
    val libSitePackages = newDir("lib-site-packages")
    newFile(newDir(libSitePackages, LIB_ENV_PACKAGE), "__init__.py")
    newFile(rootOf(lib.get()), "$LIB_SOURCE.py")
    for (pyProject in listOf(app.get(), lib.get())) newFile(rootOf(pyProject), FOOTHOLD)

    val interpreters = listOf(
      app.get() to addMockInterpreter(app.get(), newDir("app-site-packages")),
      lib.get() to addMockInterpreter(lib.get(), libSitePackages),
    )
    try {
      ModuleRootModificationUtil.addDependency(app.get().residesOnModule, lib.get().residesOnModule)
      // The legacy flow transfers the roots on the EDT, and the interpreter library flow gives them with its sync.
      withContext(Dispatchers.EDT) { }
      PyInterpreterLibrarySync.getInstance(project).syncForTests()
      IndexingTestUtil.suspendUntilIndexesAreReady(project)
      body()
    }
    finally {
      val registry = PythonInterpreterRegistry.getInstance(project)
      for ((pyProject, interpreter) in interpreters) {
        pyProject.setPythonInterpreter(null)
        registry.remove(interpreter)
      }
    }
  }

  /** Makes a mock interpreter the interpreter of [pyProject]. Its `sys.path` is [sitePackages]. */
  private suspend fun addMockInterpreter(pyProject: PyProject, sitePackages: VirtualFile): PythonInterpreter {
    val project = projectFixture.get()
    val sdk = PythonMockSdk.create()
    sdk.putUserData(PythonMockSdkKeys.SYS_PATH, listOf(sitePackages.path))
    val interpreter = PythonInterpreterRegistry.getInstance(project).addMockPythonInterpreter(pyProject, sdk)
    pyProject.setPythonInterpreter(interpreter).orThrow()
    assertThat(updateInterpreterFacts(interpreter, project).successOrNull).isNotNull()
    return interpreter
  }

  /** What an absolute import of [name] resolves to in a file of [pyProject]. */
  private suspend fun resolve(name: String, pyProject: PyProject): List<PsiElement> = readAction {
    val file = checkNotNull(rootOf(pyProject).findChild(FOOTHOLD)) { "No $FOOTHOLD in ${pyProject.baseDir}" }
    val foothold = checkNotNull(PsiManager.getInstance(projectFixture.get()).findFile(file))
    resolveQualifiedName(QualifiedName.fromDottedString(name), fromFoothold(foothold))
  }

  private fun rootOf(pyProject: PyProject): VirtualFile =
    checkNotNull(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(pyProject.baseDir))

  private fun newDir(name: String): VirtualFile =
    checkNotNull(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(Files.createDirectories(tempDir.get().resolve(name))))

  private fun newDir(parent: VirtualFile, name: String): VirtualFile =
    checkNotNull(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(Files.createDirectories(parent.toNioPath().resolve(name))))

  private fun newFile(parent: VirtualFile, name: String): VirtualFile =
    checkNotNull(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(Files.createFile(parent.toNioPath().resolve(name))))

  private companion object {
    const val LIB_ENV_PACKAGE = "only_in_lib_env"
    const val LIB_SOURCE = "lib_source"
    const val FOOTHOLD = "main.py"
  }
}

/** [PyDependencyModuleInterpreterResolveTest] in the legacy flow, where the SDK of the module names its interpreter. */
@RegistryKeyAppLevel(key = "python.interpreter.library.entries", value = "false")
@Subsystems.CodeInsight
@Layers.Functional
internal class PyDependencyModuleInterpreterResolveLegacyFlowTest : PyDependencyModuleInterpreterResolveTest()

/** [PyDependencyModuleInterpreterResolveTest] in the interpreter library flow, where a library entry names the interpreter. */
@RegistryKeyAppLevel(key = "python.interpreter.library.entries", value = "true")
@Subsystems.CodeInsight
@Layers.Functional
internal class PyDependencyModuleInterpreterResolveLibraryFlowTest : PyDependencyModuleInterpreterResolveTest()
