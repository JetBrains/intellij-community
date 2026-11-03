// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.env.tests.interpreters.lspTools

import com.intellij.python.pyproject.model.evolution.getInterpreter
import com.intellij.python.pyproject.model.evolution.setPythonInterpreter
import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.idea.TestFor
import com.intellij.openapi.application.EDT
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspClient
import com.intellij.platform.lsp.api.LspClientManager
import com.intellij.platform.lsp.api.LspServerState
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightFixture
import com.intellij.python.junit5Tests.framework.LeakedProcessReporterExtension
import com.intellij.python.junit5Tests.framework.env.PyEnvTestCase
import com.intellij.python.junit5Tests.framework.env.pyEnvironmentFixture
import com.intellij.python.junit5Tests.framework.pyProjectFixture
import com.intellij.python.junit5Tests.framework.subdirectoryFixture
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
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.ExtendWith
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.minutes

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
      LspClientManager.getInstance(project).getClients(RUFF).count { client -> client.state == LspServerState.Running } == 2
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
    assertEquals(listOf(serviceModule), client.pyServedModules, "the service module keeps a server of its own")
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

      awaitOneRuffServer(pinnedRuffVersion)
      awaitUnusedImport(serviceFile) { isOneRuffServer(it, pinnedRuffVersion) }
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

      awaitOneRuffServer(SERVICE_RUFF_VERSION)
      awaitUnusedImport(rootFile) { isOneRuffServer(it, SERVICE_RUFF_VERSION) }
      assertRuffChecks(serviceFile, serviceModule, SERVICE_RUFF_VERSION)
      awaitOneRuffServer(SERVICE_RUFF_VERSION)
    }

  @AfterEach
  fun tearDownTool(): Unit = timeoutRunBlocking {
    tearDownLspTool(project, RUFF)
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
    return writeModuleFile(project, projectPath.get(), rootModule, UNUSED_IMPORT_SNIPPET) to
      writeModuleFile(project, servicePath.get(), serviceModule, UNUSED_IMPORT_SNIPPET)
  }

  /** Opens [file], and checks that the Ruff server of [module] runs Ruff [expectedVersion] and reports the unused import. */
  private suspend fun assertRuffChecks(file: VirtualFile, module: Module, expectedVersion: String) {
    withContext(Dispatchers.EDT) { codeInsightFixture.configureFromExistingVirtualFile(file) }
    // The package snapshot starts empty, so the first server can hold both modules until the versions are known. It
    // restarts then, and the restart can come after any of the waits below. So no wait keeps the client of a step.
    awaitRuffClientOf(module, "the Ruff server of module '${module.name}' must run Ruff $expectedVersion") {
      it.ruffVersion == expectedVersion
    }
    awaitFileOpenedByLspTool(project, file, RUFF)
    awaitUnusedImport(file) { module in it.pyServedModules && it.ruffVersion == expectedVersion }
  }

  /** Waits until the running Ruff client that [condition] accepts reports the unused import in [file]. */
  private suspend fun awaitUnusedImport(file: VirtualFile, condition: (LspClient) -> Boolean) {
    awaitLspDiagnostics(project, file, RUFF, { runningLspClient(project, RUFF, condition) }) { diagnostics ->
      diagnostics.any { it.code?.get()?.toString() == "F401" }
    }
  }

  /** Waits until the project has one Ruff client, which runs Ruff [version] and serves both modules. */
  private suspend fun awaitOneRuffServer(version: String) {
    awaitRuffClientOf(rootModule, "no single Ruff $version server serves both modules") { isOneRuffServer(it, version) }
  }

  private fun isOneRuffServer(client: LspClient, version: String): Boolean =
    client.ruffVersion == version && isOnlyLspServerOf(client, RUFF, setOf(rootModule, serviceModule))

  private val LspClient.ruffVersion: String? get() = initializeResult?.serverInfo?.version?.substringBefore(' ')

  private suspend fun awaitRuffClientOf(
    module: Module,
    failure: String = "no running Ruff client serves module '${module.name}'",
    condition: (LspClient) -> Boolean = { true },
  ): LspClient = awaitLspClientOf(project, RUFF, module, failure, condition)
}

private val RUFF = RuffLspIntegrationProvider::class.java
