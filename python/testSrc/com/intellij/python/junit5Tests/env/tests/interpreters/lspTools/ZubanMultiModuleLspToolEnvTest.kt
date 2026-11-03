// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.env.tests.interpreters.lspTools

import com.intellij.idea.TestFor
import com.intellij.openapi.application.EDT
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspClient
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightFixture
import com.intellij.python.junit5Tests.framework.LeakedProcessReporterExtension
import com.intellij.python.junit5Tests.framework.env.PyEnvTestCase
import com.intellij.python.junit5Tests.framework.env.pyEnvironmentFixture
import com.intellij.python.junit5Tests.framework.pyModuleFixture
import com.intellij.python.junit5Tests.framework.pyProjectFixture
import com.intellij.python.junit5Tests.framework.subdirectoryFixture
import com.intellij.python.pyproject.model.evolution.setPythonInterpreter
import com.intellij.python.pytools.backend.PyToolsState
import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.python.test.env.junit5.LspToolVersions
import com.intellij.python.test.env.junit5.installToolPackage
import com.intellij.python.test.env.junit5.pyVenvFixture
import com.intellij.python.zuban.ZubanLspIntegrationProvider
import com.intellij.python.zuban.zubanPyTool
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.project.PyProject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.lsp4j.Diagnostic
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.ExtendWith
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.minutes

/**
 * `requests` is in no venv, and zuban knows that `types-requests` has its stubs. So the file reports
 * `import-untyped` exactly when the interpreter of its server holds no `types-requests`. The assignment
 * error shows that zuban checked the file, whatever the interpreter holds.
 */
private const val STUBS_SNIPPET = "import requests\nx: int = \"s\"\n"

/**
 * A project with a module nested in it, and each one with its own `.venv`.
 *
 * Zuban takes one interpreter for all the folders of a server, so the modules share a server only when
 * they share an interpreter, see [ZubanLspIntegrationProvider]. Both venvs hold the same zuban version, so
 * only the interpreter tells the two groups apart.
 */
@Subsystems.LspTools
@Layers.Functional
@TestApplication
@PyEnvTestCase
@Timeout(value = 15, unit = TimeUnit.MINUTES)
@ExtendWith(LeakedProcessReporterExtension::class)
@TestFor(issues = ["PY-85009"])
class ZubanMultiModuleLspToolEnvTest {
  private val projectPath = tempPathFixture(prefix = "zuban_multi_module")
  private val servicePath = projectPath.subdirectoryFixture("service")
  private val projectFixture = projectFixture(projectPath, openAfterCreation = true)
  private val rootModuleFixture = projectFixture.pyModuleFixture(projectPath, addPathToSourceRoot = true)
  private val serviceModuleFixture = projectFixture.pyModuleFixture(servicePath, addPathToSourceRoot = true)
  private val rootPyProjectFixture = rootModuleFixture.pyProjectFixture()
  private val servicePyProjectFixture = serviceModuleFixture.pyProjectFixture()
  private val environmentFixture = pyEnvironmentFixture()
  private val rootInterpreterFixture = environmentFixture.pyVenvFixture(where = projectPath, pyProjectFixture = rootPyProjectFixture)
  private val serviceInterpreterFixture = environmentFixture.pyVenvFixture(where = servicePath, pyProjectFixture = servicePyProjectFixture)

  private val project: Project by projectFixture
  private val rootModule: Module by rootModuleFixture
  private val serviceModule: Module by serviceModuleFixture
  private val rootPyProject: PyProject by rootPyProjectFixture
  private val servicePyProject: PyProject by servicePyProjectFixture
  private val rootInterpreter: PythonInterpreter by rootInterpreterFixture
  private val codeInsightFixture by codeInsightFixture(projectFixture, projectPath)

  @Test
  fun `modules with one interpreter share one server`(): Unit = timeoutRunBlocking(12.minutes) {
    val (rootFile, serviceFile) = setUpModules(oneInterpreter = true)

    awaitOneZubanServer()
    awaitStubsError(rootFile, ::isOneZubanServer)
    awaitStubsError(serviceFile, ::isOneZubanServer)
  }

  @Test
  fun `modules with different interpreters get a server each`(): Unit = timeoutRunBlocking(12.minutes) {
    val (rootFile, serviceFile) = setUpModules(oneInterpreter = false)
    servicePyProject.installToolPackage(LspToolVersions.requirement("types-requests"))

    val servesRootOnly = { client: LspClient -> client.pyServedModules == listOf(rootModule) }
    val servesServiceOnly = { client: LspClient -> client.pyServedModules == listOf(serviceModule) }
    awaitZubanClientOf(rootModule, "the root module needs a server of its own", servesRootOnly)
    awaitZubanClientOf(serviceModule, "the service module needs a server of its own", servesServiceOnly)
    awaitStubsError(rootFile, servesRootOnly)
    // The stubs are only in the service venv, so its server must use that interpreter.
    awaitZubanErrors(serviceFile, servesServiceOnly) { errors -> errors.none { it.code?.left == "import-untyped" } }
  }

  @Test
  fun `a module that moves to the interpreter of another group joins its server`(): Unit = timeoutRunBlocking(12.minutes) {
    val (rootFile, serviceFile) = setUpModules(oneInterpreter = false)
    awaitZubanClientOf(serviceModule, "the service module needs a server of its own") { it.pyServedModules == listOf(serviceModule) }

    servicePyProject.setPythonInterpreter(rootInterpreter).orThrow()

    awaitOneZubanServer()
    awaitStubsError(rootFile, ::isOneZubanServer)
    awaitStubsError(serviceFile, ::isOneZubanServer)
  }

  @AfterEach
  fun tearDownTool(): Unit = timeoutRunBlocking {
    tearDownLspTool(project, ZUBAN)
  }

  /**
   * Gives each module the interpreter of its own venv, enables zuban, installs the pinned zuban into each interpreter,
   * and opens one file of each module. With [oneInterpreter], the service module uses the interpreter of the root
   * module instead. Answers the files of the root module and of the service module.
   */
  private suspend fun setUpModules(oneInterpreter: Boolean): Pair<VirtualFile, VirtualFile> {
    serviceInterpreterFixture.get()
    if (oneInterpreter) servicePyProject.setPythonInterpreter(rootInterpreter).orThrow()
    PyToolsState.getInstance(project).setEnabled(zubanPyTool(), true)
    val zuban = LspToolVersions.requirement(zubanPyTool())
    rootPyProject.installToolPackage(zuban)
    if (!oneInterpreter) servicePyProject.installToolPackage(zuban)
    val files = writeModuleFile(project, projectPath.get(), rootModule, STUBS_SNIPPET) to
      writeModuleFile(project, servicePath.get(), serviceModule, STUBS_SNIPPET)
    withContext(Dispatchers.EDT) {
      codeInsightFixture.configureFromExistingVirtualFile(files.first)
      codeInsightFixture.configureFromExistingVirtualFile(files.second)
    }
    return files
  }

  /** Waits until the running zuban client that [condition] accepts reports the missing stubs in [file]. */
  private suspend fun awaitStubsError(file: VirtualFile, condition: (LspClient) -> Boolean) {
    awaitZubanErrors(file, condition) { errors -> errors.any { it.code?.left == "import-untyped" } }
  }

  /**
   * Waits until the diagnostics for [file] of the running zuban client that [condition] accepts hold the assignment
   * error and satisfy [until]. Each check looks the client up again, because a server can restart meanwhile.
   */
  private suspend fun awaitZubanErrors(file: VirtualFile, condition: (LspClient) -> Boolean, until: (List<Diagnostic>) -> Boolean) {
    awaitFileOpenedByLspTool(project, file, ZUBAN)
    awaitLspDiagnostics(project, file, ZUBAN, { runningLspClient(project, ZUBAN, condition) }) { diagnostics ->
      diagnostics.any { it.code?.left == "assignment" } && until(diagnostics)
    }
  }

  /** Waits until the project has one zuban client, which serves both modules. */
  private suspend fun awaitOneZubanServer() {
    awaitZubanClientOf(rootModule, "no single zuban server serves both modules", ::isOneZubanServer)
  }

  private fun isOneZubanServer(client: LspClient): Boolean = isOnlyLspServerOf(client, ZUBAN, setOf(rootModule, serviceModule))

  private suspend fun awaitZubanClientOf(
    module: Module,
    failure: String,
    condition: (LspClient) -> Boolean = { true },
  ): LspClient = awaitLspClientOf(project, ZUBAN, module, failure, condition)
}

private val ZUBAN = ZubanLspIntegrationProvider::class.java
