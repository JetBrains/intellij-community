// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.env.tests.interpreters.lspTools

import com.intellij.python.pyproject.model.evolution.getInterpreter
import com.intellij.python.pyproject.model.evolution.setPythonInterpreter
import com.intellij.python.sdk.backend.PythonInterpreter
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
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightFixture
import com.intellij.python.junit5Tests.framework.LeakedProcessReporterExtension
import com.intellij.python.junit5Tests.framework.env.PyEnvTestCase
import com.intellij.python.junit5Tests.framework.env.pyEnvironmentFixture
import com.intellij.python.junit5Tests.framework.pyProjectFixture
import com.intellij.python.junit5Tests.framework.subdirectoryFixture
import com.intellij.python.lsp.core.pyServedProjects
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
import com.jetbrains.python.project.PyProject.Companion.asPyProject
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

/** A third Ruff version, for an interpreter outside the project. */
private const val SPARE_RUFF_VERSION = "0.13.0"

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
  private val rootPyProjectFixture = projectFixture.pyProjectFixture(projectPath)
  private val servicePyProjectFixture = projectFixture.pyProjectFixture(servicePath)
  private val projectSdkFixture = pyEnvironmentFixture().pyVenvFixture(where = projectPath, rootPyProjectFixture)
  private val serviceSdkFixture = pyEnvironmentFixture().pyVenvFixture(where = servicePath, servicePyProjectFixture)

  /** A module outside the project directory. It is a workspace of its own, so it never joins the group of a module above. */
  private val sparePath = tempPathFixture(prefix = "ruff_spare")
  private val sparePyProjectFixture = projectFixture.pyProjectFixture(sparePath)
  private val spareSdkFixture = pyEnvironmentFixture().pyVenvFixture(where = sparePath, sparePyProjectFixture)

  private val project: Project by projectFixture
  private val rootPyProject: PyProject by rootPyProjectFixture
  private val servicePyProject: PyProject by servicePyProjectFixture
  private val sparePyProject: PyProject by sparePyProjectFixture
  private val rootModule: Module get() = rootPyProject.residesOnModule
  private val serviceModule: Module get() = servicePyProject.residesOnModule

  private val projectInterpreter: PythonInterpreter get() = projectSdkFixture.get()
  private val serviceInterpreter: PythonInterpreter get() = serviceSdkFixture.get()
  private val spareInterpreter: PythonInterpreter get() = spareSdkFixture.get()
  private val codeInsightFixture by codeInsightFixture(projectFixture, projectPath)

  private val pinnedRuffVersion = LspToolVersions.requirement(RuffPyTool.getInstance()).substringAfter("==")

  @Test
  fun `each module runs the ruff of its own interpreter`(): Unit = timeoutRunBlocking(12.minutes) {
    val (rootFile, serviceFile) = setUpModules()

    assertRuffChecks(serviceFile, serviceModule, SERVICE_RUFF_VERSION)
    assertRuffChecks(rootFile, rootModule, pinnedRuffVersion)
    awaitRuffClientOf(rootModule, "two Ruff versions need two servers") {
      LspClientManager.getInstance(project).getClients<RuffLspIntegrationProvider>().count { client -> client.state == LspServerState.Running } == 2
    }
  }

  /**
   * The service module moves to an interpreter outside the project, with a third Ruff version. Its group stays the
   * same, so no group restart runs. Only the interpreter listener of the provider restarts its server.
   */
  @Test
  fun `a new module interpreter in the same group restarts the server of the module`(): Unit = timeoutRunBlocking(12.minutes) {
    val (_, serviceFile) = setUpModules()
    sparePyProject.installToolPackage("ruff==$SPARE_RUFF_VERSION")
    assertRuffChecks(serviceFile, serviceModule, SERVICE_RUFF_VERSION)

    servicePyProject.setPythonInterpreter(spareInterpreter).orThrow()

    val client = awaitRuffClientOf(serviceModule, "the Ruff server of module 'service' did not restart with Ruff $SPARE_RUFF_VERSION") {
      it.ruffVersion == SPARE_RUFF_VERSION
    }
    assertEquals(listOf(serviceModule), client.pyServedModulesForTest, "the service module keeps a server of its own")
  }

  @Test
  fun `modules with one interpreter share one server`(): Unit = timeoutRunBlocking(12.minutes) {
    val (rootFile, serviceFile) = setUpModules(oneInterpreter = true)

    assertRuffChecks(serviceFile, serviceModule, pinnedRuffVersion)
    assertRuffChecks(rootFile, rootModule, pinnedRuffVersion)
    awaitOneRuffServer(pinnedRuffVersion)
  }

  /** The service module moves to the interpreter of the root module, so it joins the server of that interpreter. */
  @Test
  fun `a module that moves to the interpreter of another module moves to the server of that interpreter`(): Unit =
    timeoutRunBlocking(12.minutes) {
      val (rootFile, serviceFile) = setUpModules()
      assertRuffChecks(serviceFile, serviceModule, SERVICE_RUFF_VERSION)

      servicePyProject.setPythonInterpreter(projectInterpreter).orThrow()

      val client = awaitOneRuffServer(pinnedRuffVersion)
      awaitLspDiagnostics(client, serviceFile) { it.code?.get()?.toString() == "F401" }
      assertRuffChecks(rootFile, rootModule, pinnedRuffVersion)
      awaitOneRuffServer(pinnedRuffVersion)
    }

  /** The root module moves to the interpreter of the service module, so it joins the server of that interpreter. */
  @Test
  fun `a new interpreter of the root module moves it to the server of that interpreter`(): Unit =
    timeoutRunBlocking(12.minutes) {
      val (rootFile, serviceFile) = setUpModules()
      assertRuffChecks(rootFile, rootModule, pinnedRuffVersion)

      rootPyProject.setPythonInterpreter(serviceInterpreter).orThrow()

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
   * each interpreter. With [oneInterpreter], the service module also gets the project interpreter. Answers the
   * files of the root module and of the service module.
   */
  private suspend fun setUpModules(oneInterpreter: Boolean = false): Pair<VirtualFile, VirtualFile> {
    assertNotEquals(pinnedRuffVersion, SERVICE_RUFF_VERSION, "the two interpreters must hold different Ruff versions")
    assertEquals(serviceSdkFixture.get(), serviceModule.asPyProject()?.getInterpreter(), "the service module must have its own interpreter")
    rootPyProject.setPythonInterpreter(projectInterpreter).orThrow()
    if (oneInterpreter) servicePyProject.setPythonInterpreter(projectInterpreter).orThrow()
    PyToolsState.getInstance(project).setEnabled(RuffPyTool.getInstance(), true)
    rootPyProject.installToolPackage("ruff==$pinnedRuffVersion")
    // The service module of [oneInterpreter] runs the project interpreter, which must keep the pinned Ruff.
    if (!oneInterpreter) servicePyProject.installToolPackage("ruff==$SERVICE_RUFF_VERSION")
    return writeModuleFile(projectPath.get(), rootModule) to writeModuleFile(servicePath.get(), serviceModule)
  }

  /** Opens [file], and checks that the Ruff server of [module] runs Ruff [expectedVersion] and reports the unused import. */
  private suspend fun assertRuffChecks(file: VirtualFile, module: Module, expectedVersion: String) {
    withContext(Dispatchers.EDT) { codeInsightFixture.configureFromExistingVirtualFile(file) }
    // The package snapshot starts empty, so the first server can hold both modules until the versions are known. It
    // restarts then, and a wait for the open file before that restart would fail on the shutdown.
    val client = awaitRuffClientOf(module, "the Ruff server of module '${module.name}' must run Ruff $expectedVersion") {
      it.ruffVersion == expectedVersion
    }
    awaitFileOpenedByLspTool(project, file)
    awaitLspDiagnostics(client, file) { diagnostic -> diagnostic.code?.get()?.toString() == "F401" }
  }

  /** The one Ruff client of the project, once it runs Ruff [version] and serves both modules. */
  private suspend fun awaitOneRuffServer(version: String): LspClient =
    awaitRuffClientOf(rootModule, "no single Ruff $version server serves both modules") { client ->
      client.ruffVersion == version &&
      client.pyServedModulesForTest.toSet() == setOf(rootModule, serviceModule) &&
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
          module in it.pyServedModulesForTest && it.state == LspServerState.Running && condition(it)
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

/** The modules of the Python projects that the server behind [this] answers for. */
private val LspClient.pyServedModulesForTest: List<Module>
  get() = pyServedProjects.map { it.residesOnModule }
