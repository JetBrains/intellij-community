// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.env.tests.interpreters.lspTools

import com.intellij.idea.TestFor
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.platform.lsp.api.LspClient
import com.intellij.platform.lsp.api.LspClientManager
import com.intellij.platform.lsp.api.LspServerState
import com.intellij.platform.lsp.api.getClients
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightFixture
import com.intellij.python.junit5Tests.framework.LeakedProcessReporterExtension
import com.intellij.python.junit5Tests.framework.env.PyEnvTestCase
import com.intellij.python.junit5Tests.framework.env.pySdkFixture
import com.intellij.python.junit5Tests.framework.pyModuleFixture
import com.intellij.python.junit5Tests.framework.pyProjectFixture
import com.intellij.python.junit5Tests.framework.subdirectoryFixture
import com.intellij.python.lsp.core.pyServedModules
import com.intellij.python.pytools.backend.PyToolsState
import com.intellij.python.ruff.RuffPyTool
import com.intellij.python.ruff.server.RuffLspIntegrationProvider
import com.intellij.python.test.env.junit5.LspToolVersions
import com.intellij.python.test.env.junit5.installToolPackage
import com.intellij.python.test.env.junit5.pyVenvFixture
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.project.PyProject
import com.jetbrains.python.sdk.pythonSdk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
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
 * A Ruff version that differs from the pinned one in [LspToolVersions]. Only the version that a server reports
 * tells the two environments apart.
 */
private const val SERVICE_RUFF_VERSION = "0.14.0"

/** An unused import, which Ruff reports with its default rules. The diagnostic proves that Ruff checked the file. */
private const val UNUSED_IMPORT_SNIPPET = "import os\n"

/**
 * The layout of PY-86537: a project with a module nested in it, and each one with its own `.venv`.
 *
 * The root module inherits the project interpreter. The nested `service` module has an interpreter of its own, with
 * another Ruff version in it. The module interpreter has priority over the project interpreter. So each module must
 * get a Ruff server that runs the Ruff of its own interpreter.
 *
 * Modules that run the same Ruff share one server, see [RuffLspIntegrationProvider.getDescriptor]. So when both
 * modules get one interpreter, one server must answer for both.
 */
@Subsystems.LspTools
@Layers.Functional
@TestApplication
@PyEnvTestCase
@Timeout(value = 15, unit = TimeUnit.MINUTES)
@ExtendWith(LeakedProcessReporterExtension::class)
@TestFor(issues = ["PY-86537"])
class RuffMultiModuleLspToolEnvTest {
  private val projectPath = tempPathFixture(prefix = "ruff_multi_module")
  private val servicePath = projectPath.subdirectoryFixture("service")
  private val projectFixture = projectFixture(projectPath, openAfterCreation = true)
  private val rootModuleFixture = projectFixture.pyModuleFixture(projectPath, addPathToSourceRoot = true)
  private val serviceModuleFixture = projectFixture.pyModuleFixture(servicePath, addPathToSourceRoot = true)
  private val projectSdkFixture = pySdkFixture().pyVenvFixture(where = projectPath, addToSdkTable = true)
  private val serviceSdkFixture = pySdkFixture().pyVenvFixture(where = servicePath, addToSdkTable = true, serviceModuleFixture)

  private val project: Project by projectFixture
  private val rootModule: Module by rootModuleFixture
  private val serviceModule: Module by serviceModuleFixture
  private val rootPyProject: PyProject by rootModuleFixture.pyProjectFixture()
  private val servicePyProject: PyProject by serviceModuleFixture.pyProjectFixture()
  private val projectSdk: Sdk by projectSdkFixture
  private val codeInsightFixture by codeInsightFixture(projectFixture, projectPath)

  private val pinnedRuffVersion = LspToolVersions.requirement(RuffPyTool.getInstance()).substringAfter("==")

  @Test
  fun `each module runs the ruff of its own interpreter`(): Unit = timeoutRunBlocking(12.minutes) {
    val (rootFile, serviceFile) = setUpModules()

    assertRuffChecks(serviceFile, serviceModule, SERVICE_RUFF_VERSION)
    assertRuffChecks(rootFile, rootModule, pinnedRuffVersion)
    val clients = LspClientManager.getInstance(project).getClients<RuffLspIntegrationProvider>()
    assertEquals(2, clients.size, "two Ruff versions need two servers, got ${clients.map { it.pyServedModules }}")
  }

  @Test
  fun `modules with one interpreter share one server`(): Unit = timeoutRunBlocking(12.minutes) {
    val (rootFile, serviceFile) = setUpModules(oneInterpreter = true)

    assertRuffChecks(serviceFile, serviceModule, pinnedRuffVersion)
    assertRuffChecks(rootFile, rootModule, pinnedRuffVersion)
    awaitOneRuffServer(pinnedRuffVersion)
  }

  /** The Project Structure dialog changes the module model directly, and it fires no `PySdkListener` event. */
  @Test
  fun `a new module interpreter from the project model moves the module to the server of that interpreter`(): Unit =
    timeoutRunBlocking(12.minutes) {
      val (rootFile, serviceFile) = setUpModules()
      assertRuffChecks(serviceFile, serviceModule, SERVICE_RUFF_VERSION)

      ModuleRootModificationUtil.setModuleSdk(serviceModule, projectSdk)

      val client = awaitOneRuffServer(pinnedRuffVersion)
      awaitLspDiagnostics(client, serviceFile) { it.code?.get()?.toString() == "F401" }
      assertRuffChecks(rootFile, rootModule, pinnedRuffVersion)
      awaitOneRuffServer(pinnedRuffVersion)
    }

  /** A new project interpreter is also a new interpreter of each module that inherits it, and it fires only `rootsChanged`. */
  @Test
  fun `a new project interpreter moves a module that inherits it to the server of that interpreter`(): Unit =
    timeoutRunBlocking(12.minutes) {
      val (rootFile, serviceFile) = setUpModules()
      assertRuffChecks(rootFile, rootModule, pinnedRuffVersion)

      edtWriteAction { ProjectRootManager.getInstance(project).projectSdk = serviceSdkFixture.get() }

      val client = awaitOneRuffServer(SERVICE_RUFF_VERSION)
      awaitLspDiagnostics(client, rootFile) { it.code?.get()?.toString() == "F401" }
      assertRuffChecks(serviceFile, serviceModule, SERVICE_RUFF_VERSION)
      awaitOneRuffServer(SERVICE_RUFF_VERSION)
    }

  @AfterEach
  fun tearDownTool(): Unit = timeoutRunBlocking {
    tearDownLspTool(project, RuffLspIntegrationProvider::class.java)
  }

  /**
   * Makes the project interpreter the interpreter of the root module, enables Ruff, and installs one Ruff version into
   * each interpreter. With [oneInterpreter], the service module also inherits the project interpreter. Answers the
   * files of the root module and of the service module.
   */
  private suspend fun setUpModules(oneInterpreter: Boolean = false): Pair<VirtualFile, VirtualFile> {
    assertNotEquals(pinnedRuffVersion, SERVICE_RUFF_VERSION, "the two interpreters must hold different Ruff versions")
    assertEquals(serviceSdkFixture.get(), serviceModule.pythonSdk, "the service module must have its own interpreter")
    edtWriteAction {
      ProjectRootManager.getInstance(project).projectSdk = projectSdk
      ModuleRootModificationUtil.setSdkInherited(rootModule)
      if (oneInterpreter) ModuleRootModificationUtil.setSdkInherited(serviceModule)
    }
    PyToolsState.getInstance(project).setEnabled(RuffPyTool.getInstance(), true)
    rootPyProject.installToolPackage("ruff==$pinnedRuffVersion")
    // The service module of [oneInterpreter] runs the project interpreter, which must keep the pinned Ruff.
    if (!oneInterpreter) servicePyProject.installToolPackage("ruff==$SERVICE_RUFF_VERSION")
    return writeModuleFile(projectPath.get(), rootModule) to writeModuleFile(servicePath.get(), serviceModule)
  }

  /** Opens [file], and checks that the Ruff server of [module] runs Ruff [expectedVersion] and reports the unused import. */
  private suspend fun assertRuffChecks(file: VirtualFile, module: Module, expectedVersion: String) {
    withContext(Dispatchers.EDT) { codeInsightFixture.configureFromExistingVirtualFile(file) }
    awaitFileOpenedByLspTool(project, file)
    // The package snapshot starts empty, so the first server can hold both modules until the versions are known.
    val client = awaitRuffClientOf(module, "the Ruff server of module '${module.name}' must run Ruff $expectedVersion") {
      it.ruffVersion == expectedVersion
    }
    awaitLspDiagnostics(client, file) { diagnostic -> diagnostic.code?.get()?.toString() == "F401" }
  }

  /** The one Ruff client of the project, once it runs Ruff [version] and serves both modules. */
  private suspend fun awaitOneRuffServer(version: String): LspClient =
    awaitRuffClientOf(rootModule, "no single Ruff $version server serves both modules") { client ->
      client.ruffVersion == version &&
      client.pyServedModules.toSet() == setOf(rootModule, serviceModule) &&
      LspClientManager.getInstance(project).getClients<RuffLspIntegrationProvider>().size == 1
    }

  private val LspClient.ruffVersion: String? get() = initializeResult?.serverInfo?.version?.substringBefore(' ')

  /**
   * The running Ruff client that serves [module], once [condition] holds for it. Fails with [failure] when no such
   * client comes in time.
   */
  private suspend fun awaitRuffClientOf(
    module: Module,
    failure: String = "no running Ruff client serves module '${module.name}'",
    condition: (LspClient) -> Boolean = { true },
  ): LspClient {
    val manager = LspClientManager.getInstance(project)
    val client = withTimeoutOrNull(90.seconds) {
      var found: LspClient? = null
      while (found == null) {
        found = manager.getClients<RuffLspIntegrationProvider>().firstOrNull {
          module in it.pyServedModules && it.state == LspServerState.Running && condition(it)
        }
        if (found == null) delay(200.milliseconds)
      }
      found
    }
    return client ?: fail(failure)
  }

  /** Writes [UNUSED_IMPORT_SNIPPET] into [directory], and checks that the file belongs to [module]. */
  private suspend fun writeModuleFile(directory: Path, module: Module): VirtualFile {
    val path = directory.resolve("main.py")
    withContext(Dispatchers.IO) { path.writeText(UNUSED_IMPORT_SNIPPET) }
    val file = checkNotNull(VirtualFileManager.getInstance().refreshAndFindFileByNioPath(path)) { "$path is not in the VFS" }
    val owner = readAction { ModuleUtilCore.findModuleForFile(file, project) }
    assertEquals(module, owner, "$path must belong to module '${module.name}'")
    return file
  }
}
