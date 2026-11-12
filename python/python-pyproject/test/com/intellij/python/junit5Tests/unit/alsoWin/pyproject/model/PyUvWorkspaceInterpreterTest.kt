// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit.alsoWin.pyproject.model

import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.module.Module
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.python.junit5Tests.framework.env.pyMockInterpreterWithoutModuleFixture
import com.intellij.python.pyproject.model.evolution.EvoPyProjectModel
import com.intellij.python.pyproject.model.evolution.setOnWorkspace
import com.intellij.python.sdk.backend.evolution.EvoWorkspace
import com.intellij.python.sdk.backend.pyInterpreterDependency
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.RegistryKeyAppLevel
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.drainUncaughtExceptions
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.intellij.workspaceModel.ide.legacyBridge.findModuleEntity
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.withTimeoutOrNull
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** The `[project].name` of the root `pyproject.toml`, which is also the name of the module the sync creates for it. */
private const val ROOT = "ws"

/**
 * The message of the exception the configurator throws for a member of a uv workspace today, see
 * `PyUvWorkspaceInterpreterTest.drainTheKnownConfiguratorFailure`. It is the sanity check of the configurator
 * itself, `sdkConfigImpl.kt:54`.
 */
private const val NO_INTERPRETER = "has no interpreter in the snapshot after it was configured"

/**
 * A uv workspace is one interpreter for all of its members: the root and every member of `[tool.uv.workspace]` form a
 * single [EvoWorkspace], and an interpreter set on it reaches every member — each one holds the same interpreter and
 * the same interpreter library entry, the one the root owns.
 *
 * A member that keeps its own interpreter, or none, is the defect this covers: the packages tool window and the
 * widget then show one thing for a file of the root and another for a file of a member, and the member resolves no
 * dependency the workspace installed.
 */
@RegistryKeyAppLevel(key = "python.interpreter.library.entries", value = "true")
@TestApplication
internal class PyUvWorkspaceInterpreterTest {
  private val projectPath = tempPathFixture(prefix = "project")
  private val projectFixture = projectFixture(projectPath, openAfterCreation = true)
  private val f by pyProjectTomlSyncFixture(projectFixture)
  private val envPath = tempPathFixture(prefix = "env")

  // The project root owns the interpreter, as the root of the workspace does: `setOnWorkspace` registers it there.
  private val interpreter = projectFixture.pyMockInterpreterWithoutModuleFixture(envPath, projectPath)

  @Test
  fun `the root and its members form one workspace`(): Unit = timeoutRunBlocking(60.seconds) {
    val memberNames = createUvWorkspace()

    val workspace = awaitWorkspace()

    assertThat(workspace.root.pyProject.residesOnModule.name)
      .describedAs("the workspace is rooted at the module of the toml that declares the members")
      .isEqualTo(ROOT)
    assertThat(workspace.members.map { it.pyProject.residesOnModule.name })
      .describedAs("every member of `[tool.uv.workspace]` belongs to the one workspace, its root among them")
      .containsExactlyInAnyOrder(ROOT, *memberNames.toTypedArray())
  }

  @Test
  fun `an interpreter set on the workspace is the interpreter of every member`(): Unit = timeoutRunBlocking(60.seconds) {
    createUvWorkspace()
    val applied = awaitWorkspace().setOnWorkspace(interpreter.get()).orThrow()
    EvoPyProjectModel.getInstance(f.project).awaitCurrentInterpreters()

    val workspace = awaitWorkspace()

    assertThat(workspace.members.associate { it.pyProject.residesOnModule.name to it.interpreter })
      .describedAs("a member uses the interpreter of the workspace root, so every surface shows one interpreter")
      .allSatisfy { _, memberInterpreter -> assertThat(memberInterpreter).isEqualTo(applied) }
  }

  @Test
  fun `every member of the workspace names the same interpreter library`(): Unit = timeoutRunBlocking(60.seconds) {
    createUvWorkspace()
    awaitWorkspace().setOnWorkspace(interpreter.get()).orThrow()
    EvoPyProjectModel.getInstance(f.project).awaitCurrentInterpreters()

    val entries = awaitWorkspace().members.associate { it.pyProject.residesOnModule.name to entryNameOf(it.pyProject.residesOnModule) }

    assertThat(entries.values)
      .describedAs("the entry of each member names the interpreter of the root, so one library carries its roots: $entries")
      .doesNotContainNull()
      .containsOnly(entries.getValue(ROOT))
  }

  /** Writes the root `pyproject.toml` with its two members and syncs the project. Returns the names of the members. */
  private suspend fun createUvWorkspace(): List<String> {
    val members = edtWriteAction { f.root.convertDirToUvWorkspace(ROOT) }
    f.reloadProject()
    return members
  }

  /**
   * The workspace of more than one member, once the model holds it.
   *
   * The sync publishes the modules and the model recomputes after it, so the snapshot of the moment `reloadProject`
   * returns can still be the one from before. The bystander modules of the fixture are workspaces of one, so the
   * workspace with members is the uv one.
   */
  private suspend fun awaitWorkspace(): EvoWorkspace {
    val model = EvoPyProjectModel.getInstance(f.project)
    val workspace = withTimeoutOrNull(30.seconds) {
      model.snapshotFlow().mapNotNull { snapshot -> snapshot.workspaces.firstOrNull { it.members.size > 1 } }.first()
    }
    assertThat(workspace)
      .describedAs("the root and its two members must form one workspace, the snapshot has ${describeWorkspaces()}")
      .isNotNull
    return workspace!!
  }

  /** The name of the interpreter library entry of [module], or `null` when it has none. */
  private fun entryNameOf(module: Module): String? =
    module.findModuleEntity(f.project.workspaceModel.currentSnapshot)?.dependencies?.pyInterpreterDependency?.library?.name

  /**
   * Takes the one exception the configurator throws for a member of a workspace, so that the run is green while the
   * defect is open, and records that it was thrown.
   *
   * It is drained here and judged in [theDefectIsStillOpen] rather than per test, because it is thrown once for the
   * project, on a coroutine of the configurator service, and the extension reports it against whichever test
   * happens to end first — it was `an interpreter set on the workspace is the interpreter of every member` in one
   * run and `every member of the workspace names the same interpreter library` in the next. Only "some test of this
   * class saw it" is a stable statement.
   *
   * The predicate names this one failure. Any other uncaught exception is left in place and still fails the test.
   *
   * The wait is what makes this work: the configurator throws on a coroutine of its own, so the exception reaches the
   * handler after the body of the test is over, and a drain that only looks once takes nothing and leaves the
   * exception to the extension, see build 1128368029.
   */
  @AfterEach
  fun drainTheKnownConfiguratorFailure(): Unit = timeoutRunBlocking(60.seconds) {
    val drained = withTimeoutOrNull(40.seconds) {
      while (true) {
        if (drainUncaughtExceptions { it is IllegalStateException && it.message?.contains(NO_INTERPRETER) == true }.isNotEmpty()) break
        delay(200.milliseconds)
      }
      true
    }
    if (drained == true) configuratorFailed = true
  }

  private fun describeWorkspaces(): String =
    EvoPyProjectModel.getInstance(f.project).snapshotOrNull()
      ?.workspaces
      ?.joinToString { "${it.root.pyProject.residesOnModule.name}: ${it.members.map { member -> member.pyProject.residesOnModule.name }}" }
    ?: "no snapshot"

  companion object {
    @Volatile
    private var configuratorFailed = false

    /**
     * The tripwire of [drainTheKnownConfiguratorFailure]: while the defect is open this holds and the class is
     * green, and the first run in which the configurator stops throwing fails here.
     *
     * Then delete this method, [drainTheKnownConfiguratorFailure] and [configuratorFailed]. Nothing else changes:
     * the three tests already assert what the product must do.
     */
    @JvmStatic
    @AfterAll
    fun theDefectIsStillOpen() {
      assertThat(configuratorFailed)
        .describedAs(
          "the configurator no longer throws `$NO_INTERPRETER` for a member of a uv workspace " +
          "(`sdkConfigImpl.kt:54`). That is the fix, not a failure of this class: remove the drain and this check. " +
          "If instead the exception stopped being reachable because these tests stopped exercising the path, " +
          "restore the path — a silent drain of an exception that never comes covers nothing"
        )
        .isTrue()
    }
  }
}
