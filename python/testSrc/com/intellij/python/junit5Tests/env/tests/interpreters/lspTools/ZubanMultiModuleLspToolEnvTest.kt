// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.env.tests.interpreters.lspTools

import com.intellij.idea.TestFor
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.platform.lsp.api.LspClient
import com.intellij.platform.lsp.api.LspClientManager
import com.intellij.platform.lsp.api.LspServerState
import com.intellij.platform.lsp.api.getClients
import com.intellij.platform.lsp.impl.LspClientImpl
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightFixture
import com.intellij.python.junit5Tests.framework.LeakedProcessReporterExtension
import com.intellij.python.junit5Tests.framework.env.PyEnvTestCase
import com.intellij.python.junit5Tests.framework.env.pyEnvironmentFixture
import com.intellij.python.junit5Tests.framework.pyModuleFixture
import com.intellij.python.junit5Tests.framework.pyProjectFixture
import com.intellij.python.junit5Tests.framework.subdirectoryFixture
import com.intellij.python.lsp.core.pyServedProjects
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.eclipse.lsp4j.Diagnostic
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.ExtendWith
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.writeText
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

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

    val client = awaitOneZubanServer()
    awaitStubsError(client, rootFile)
    awaitStubsError(client, serviceFile)
  }

  @Test
  fun `modules with different interpreters get a server each`(): Unit = timeoutRunBlocking(12.minutes) {
    val (rootFile, serviceFile) = setUpModules(oneInterpreter = false)
    servicePyProject.installToolPackage(LspToolVersions.requirement("types-requests"))

    val rootClient = awaitZubanClientOf(rootModule, "the root module needs a server of its own") { it.pyServedModulesForTest == listOf(rootModule) }
    val serviceClient = awaitZubanClientOf(serviceModule, "the service module needs a server of its own") {
      it.pyServedModulesForTest == listOf(serviceModule)
    }
    awaitStubsError(rootClient, rootFile)
    // The stubs are only in the service venv, so its server must use that interpreter.
    awaitZubanErrors(serviceClient, serviceFile) { errors -> errors.none { it.code?.left == "import-untyped" } }
  }

  @Test
  fun `a module that moves to the interpreter of another group joins its server`(): Unit = timeoutRunBlocking(12.minutes) {
    val (rootFile, serviceFile) = setUpModules(oneInterpreter = false)
    awaitZubanClientOf(serviceModule, "the service module needs a server of its own") { it.pyServedModulesForTest == listOf(serviceModule) }

    servicePyProject.setPythonInterpreter(rootInterpreter).orThrow()

    val client = awaitOneZubanServer()
    awaitStubsError(client, rootFile)
    awaitStubsError(client, serviceFile)
  }

  @AfterEach
  fun tearDownTool(): Unit = timeoutRunBlocking {
    tearDownLspTool(project, ZubanLspIntegrationProvider::class.java)
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
    val files = writeModuleFile(projectPath.get(), rootModule) to writeModuleFile(servicePath.get(), serviceModule)
    withContext(Dispatchers.EDT) {
      codeInsightFixture.configureFromExistingVirtualFile(files.first)
      codeInsightFixture.configureFromExistingVirtualFile(files.second)
    }
    return files
  }

  private suspend fun awaitStubsError(client: LspClient, file: VirtualFile) {
    awaitZubanErrors(client, file) { errors -> errors.any { it.code?.left == "import-untyped" } }
  }

  /** Waits until the diagnostics of [client] for [file] hold the assignment error and satisfy [until]. */
  private suspend fun awaitZubanErrors(client: LspClient, file: VirtualFile, until: (List<Diagnostic>) -> Boolean) {
    awaitFileOpenedByLspTool(project, file)
    val reached = withTimeoutOrNull(2.minutes) {
      while (true) {
        val diagnostics = diagnosticsOf(client, file)
        if (diagnostics.any { it.code?.left == "assignment" } && until(diagnostics)) break
        delay(500.milliseconds)
      }
    }
    if (reached == null) fail<Unit>("The diagnostics of ${file.path} did not reach the expected state: ${diagnosticsOf(client, file)}")
  }

  private suspend fun diagnosticsOf(client: LspClient, file: VirtualFile): List<Diagnostic> =
    readAction { (client as LspClientImpl).getDiagnosticsAndQuickFixes(file) }.map { it.diagnostic }

  /** The one zuban client of the project, once it serves both modules. */
  private suspend fun awaitOneZubanServer(): LspClient =
    awaitZubanClientOf(rootModule, "no single zuban server serves both modules") { client ->
      client.pyServedModulesForTest.toSet() == setOf(rootModule, serviceModule) &&
      LspClientManager.getInstance(project).getClients<ZubanLspIntegrationProvider>().size == 1
    }

  /**
   * The running zuban client that serves [module], once [condition] holds for it. Fails with [failure] when no such
   * client comes in time.
   */
  private suspend fun awaitZubanClientOf(
    module: Module,
    failure: String,
    condition: (LspClient) -> Boolean = { true },
  ): LspClient {
    val manager = LspClientManager.getInstance(project)
    val client = withTimeoutOrNull(90.seconds) {
      var found: LspClient? = null
      while (found == null) {
        found = manager.getClients<ZubanLspIntegrationProvider>().firstOrNull {
          module in it.pyServedModulesForTest && it.state == LspServerState.Running && condition(it)
        }
        if (found == null) delay(200.milliseconds)
      }
      found
    }
    return client ?: fail(failure)
  }

  /** Writes [STUBS_SNIPPET] into [directory], and checks that the file belongs to [module]. */
  private suspend fun writeModuleFile(directory: Path, module: Module): VirtualFile {
    val path = directory.resolve("main.py")
    withContext(Dispatchers.IO) { path.writeText(STUBS_SNIPPET) }
    val file = checkNotNull(VirtualFileManager.getInstance().refreshAndFindFileByNioPath(path)) { "$path is not in the VFS" }
    val owner = readAction { ModuleUtilCore.findModuleForFile(file, project) }
    assertEquals(module, owner, "$path must belong to module '${module.name}'")
    return file
  }
}

/** The modules of the Python projects that the server behind [this] answers for. */
private val LspClient.pyServedModulesForTest: List<Module>
  get() = pyServedProjects.map { it.residesOnModule }
