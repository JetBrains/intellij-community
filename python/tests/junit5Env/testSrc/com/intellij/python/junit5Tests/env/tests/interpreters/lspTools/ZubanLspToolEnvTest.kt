// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.env.tests.interpreters.lspTools

import com.intellij.idea.TestFor
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.components.service
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.util.messageIfStringOrEmpty
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightFixture
import com.intellij.python.junit5Tests.framework.env.PyEnvTestCase
import com.intellij.python.junit5Tests.framework.env.pyEnvironmentFixture
import com.intellij.python.junit5Tests.framework.pyModuleFixture
import com.intellij.python.junit5Tests.framework.pyProjectFixture
import com.intellij.python.test.env.junit5.LspToolVersions
import com.intellij.python.test.env.junit5.installToolPackage
import com.intellij.python.test.env.junit5.pyVenvFixture
import com.intellij.python.zuban.ZubanConfiguration
import com.intellij.python.zuban.ZubanLspIntegrationProvider
import com.intellij.python.zuban.common.ZubanTypeCheckingMode
import com.intellij.python.zuban.zubanPyTool
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import org.eclipse.lsp4j.Diagnostic
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.time.Duration.Companion.minutes

/**
 * End-to-end test of the zuban LSP tool support against the real `zuban server`.
 *
 * The venv of the module lies outside the content root on purpose. Zuban finds a venv inside its first
 * workspace folder by itself, so only a venv outside it shows that the initialization options name the
 * interpreter of the module.
 */
@Subsystems.LspTools
@Layers.Functional
@TestApplication
@PyEnvTestCase
@TestFor(issues = ["PY-85009"])
@Timeout(value = 10, unit = TimeUnit.MINUTES)
class ZubanLspToolEnvTest {
  private suspend fun enableZubanAndInstall() = pyProject.enableLspToolAndInstall(
    project = project,
    pyTool = zubanPyTool(),
    toolInstalled = toolInstalled,
  ) {
    project.service<ZubanConfiguration>().apply {
      inspections = true
    }
  }

  @Test
  fun `reports a type error diagnostic`(): Unit = timeoutRunBlocking(timeout = 5.minutes) {
    enableZubanAndInstall()
    val file = codeInsightFixture.configureByText("typecheck.py", "x: int = \"not an int\"\n")
    val errors = awaitZubanErrors(file.virtualFile)
    assertTrue(errors.any { it.code?.left == "assignment" && "variable has type \"int\"" in it.messageIfStringOrEmpty }) {
      "Expected zuban to report the int assignment, got: $errors"
    }
  }

  @Test
  fun `resolves a package of the module interpreter`(): Unit = timeoutRunBlocking(timeout = 5.minutes) {
    enableZubanAndInstall()
    // The `zuban` package itself is installed in the venv, and nowhere else.
    val file = codeInsightFixture.configureByText("interpreter.py", "import zuban\nx: int = \"s\"\n")
    val errors = awaitZubanErrors(file.virtualFile) { errors -> errors.any { it.code?.left == "assignment" } }
    assertTrue(errors.none { it.code?.left == "import-not-found" }) {
      "Expected zuban to find the `zuban` package of the module venv, got: $errors"
    }
  }

  @Test
  fun `resolves a module of a source root`(): Unit = timeoutRunBlocking(timeout = 5.minutes) {
    enableZubanAndInstall()
    addSourceRootWithModule("lib_initial", "initial_module")
    val file = codeInsightFixture.configureByText("source_root.py", "from initial_module import V\nx: str = V\n")
    // `V` is `Any` when the import fails, and then zuban reports no assignment.
    val errors = awaitZubanErrors(file.virtualFile) { errors -> errors.any(::isIntToStrAssignment) }
    assertTrue(errors.none { it.code?.left == "import-not-found" }) { "Expected zuban to find the module, got: $errors" }
  }

  @Test
  fun `a new source root restarts the server`(): Unit = timeoutRunBlocking(timeout = 5.minutes) {
    enableZubanAndInstall()
    writeModule("lib_later", "later_module")
    val file = codeInsightFixture.configureByText("later_source_root.py", "from later_module import V\nx: str = V\n")
    awaitZubanErrors(file.virtualFile) { errors -> errors.any { it.code?.left == "import-not-found" } }

    markSourceRoot("lib_later")
    val errors = awaitZubanErrors(file.virtualFile) { errors -> errors.any(::isIntToStrAssignment) }
    assertTrue(errors.none { it.code?.left == "import-not-found" }) { "Expected the restarted zuban to find the module, got: $errors" }
  }

  @Test
  fun `an installed stub package clears the missing stubs error`(): Unit = timeoutRunBlocking(timeout = 5.minutes) {
    enableZubanAndInstall()
    // `requests` is not installed, and zuban knows that `types-requests` has its stubs.
    val file = codeInsightFixture.configureByText("stubs.py", "import requests\nx: int = \"s\"\n")
    awaitZubanErrors(file.virtualFile) { errors -> errors.any { it.code?.left == "import-untyped" } }

    // The IDE keeps the pulled results until something asks for them again. Zuban sees the new stubs by
    // itself, so the package listener only has to drop the old results.
    pyProject.installToolPackage(LspToolVersions.requirement("types-requests"))
    awaitZubanErrors(file.virtualFile) { errors -> errors.isNotEmpty() && errors.none { it.code?.left == "import-untyped" } }
  }

  @Test
  fun `a type checking mode from the settings restarts the server in that mode`(): Unit = timeoutRunBlocking(timeout = 5.minutes) {
    enableZubanAndInstall()
    // Without a project config zuban runs in default mode, which checks the body of an unannotated
    // function. Mypy mode does not.
    val file = codeInsightFixture.configureByText("mode.py", "def f():\n    x: int = \"s\"\ny: int = \"s\"\n")
    awaitZubanErrors(file.virtualFile) { errors -> errors.count { it.code?.left == "assignment" } == 2 }

    val tool = zubanPyTool()
    tool.applyConfigurationState(project, tool.configurationState(project).copy(typeCheckingMode = ZubanTypeCheckingMode.MYPY.value))
    val errors = awaitZubanErrors(file.virtualFile) { errors -> errors.isNotEmpty() && errors.all { it.range.start.line == 2 } }
    assertTrue(errors.single().code?.left == "assignment") { "Expected only the module-level assignment in Mypy mode, got: $errors" }
  }

  @AfterEach
  fun tearDownTool(): Unit = timeoutRunBlocking {
    project.service<ZubanConfiguration>().typeCheckingMode = ZubanTypeCheckingMode.AUTO
    tearDownLspTool(project, ZubanLspIntegrationProvider::class.java)
  }

  private suspend fun awaitZubanErrors(
    file: VirtualFile,
    until: (List<Diagnostic>) -> Boolean = { it.isNotEmpty() },
  ): List<Diagnostic> = awaitLspErrorDiagnostics(project, file, ZubanLspIntegrationProvider::class.java, until)

  private fun isIntToStrAssignment(diagnostic: Diagnostic): Boolean =
    diagnostic.code?.left == "assignment" && "expression has type \"int\", variable has type \"str\"" in diagnostic.messageIfStringOrEmpty

  /** Writes `<directory>/<moduleName>.py`, which defines `V: int`. */
  private fun writeModule(directory: String, moduleName: String) {
    val path = projectPath.resolve(directory).createDirectories()
    path.resolve("$moduleName.py").writeText("V: int = 1\n")
    LocalFileSystem.getInstance().refreshNioFiles(listOf(path), false, true, null)
  }

  private suspend fun markSourceRoot(directory: String) {
    val root = VfsUtil.findFile(projectPath.resolve(directory), true) ?: error("No directory $directory")
    edtWriteAction {
      ModuleRootModificationUtil.updateModel(module) { model ->
        val entry = model.contentEntries.first { VfsUtil.isAncestor(it.file!!, root, false) }
        entry.addSourceFolder(root, false)
      }
    }
  }

  private suspend fun addSourceRootWithModule(directory: String, moduleName: String) {
    writeModule(directory, moduleName)
    markSourceRoot(directory)
  }

  companion object {
    private val toolInstalled = AtomicBoolean(false)
    private val tempPathFixture = tempPathFixture(prefix = "zuban_project")
    internal val projectPath by tempPathFixture
    private val venvPathFixture = tempPathFixture(prefix = "zuban_venv")
    private val projectFixture = projectFixture(openAfterCreation = true)
    internal val project by projectFixture
    private val moduleFixture = projectFixture.pyModuleFixture(tempPathFixture, addPathToSourceRoot = true)
    internal val module by moduleFixture
    private val pyProjectFixture = moduleFixture.pyProjectFixture()
    internal val pyProject by pyProjectFixture
    internal val venv by pyEnvironmentFixture().pyVenvFixture(where = venvPathFixture, pyProjectFixture = pyProjectFixture)
    internal val codeInsightFixture by codeInsightFixture(projectFixture, tempPathFixture)
  }
}
