// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.env.tests.interpreters.lspTools

import com.intellij.idea.TestFor
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspClient
import com.intellij.platform.lsp.api.LspClientManager
import com.intellij.platform.lsp.api.LspServerState
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
  private val rootModuleFixture = projectFixture.pyModuleFixture(projectPath, addPathToSourceRoot = true)
  private val serviceModuleFixture = projectFixture.pyModuleFixture(servicePath, addPathToSourceRoot = true)
  private val projectSdkFixture = pySdkFixture().pyVenvFixture(where = projectPath, addToSdkTable = true)
  private val serviceSdkFixture = pySdkFixture().pyVenvFixture(where = servicePath, addToSdkTable = true, serviceModuleFixture)

  /** A module outside the project directory. It is a workspace of its own, so it never joins the group of a module above. */
  private val sparePath = tempPathFixture(prefix = "ruff_spare")
  private val spareModuleFixture = projectFixture.pyModuleFixture(sparePath, addPathToSourceRoot = true)
  private val spareSdkFixture = pySdkFixture().pyVenvFixture(where = sparePath, addToSdkTable = true, spareModuleFixture)

  private val project: Project by projectFixture
  private val rootModule: Module by rootModuleFixture
  private val serviceModule: Module by serviceModuleFixture
  private val rootPyProject: PyProject by rootModuleFixture.pyProjectFixture()
  private val servicePyProject: PyProject by serviceModuleFixture.pyProjectFixture()
  private val sparePyProject: PyProject by spareModuleFixture.pyProjectFixture()
  private val projectSdk: Sdk by projectSdkFixture
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

    ModuleRootModificationUtil.setModuleSdk(serviceModule, spareSdkFixture.get())

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

  /** The Project Structure dialog changes the module model directly, and it fires no `PySdkListener` event. */
  @Test
  fun `a new module interpreter from the project model moves the module to the server of that interpreter`(): Unit =
    timeoutRunBlocking(12.minutes) {
      val (rootFile, serviceFile) = setUpModules()
      assertRuffChecks(serviceFile, serviceModule, SERVICE_RUFF_VERSION)

      ModuleRootModificationUtil.setModuleSdk(serviceModule, projectSdk)

      awaitOneRuffServer(pinnedRuffVersion)
      awaitUnusedImport(serviceFile) { isOneRuffServer(it, pinnedRuffVersion) }
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
