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
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** The `[project].name` of the root `pyproject.toml`, which is also the name of the module the sync creates for it. */
private const val ROOT = "ws"

/**
 * The message of the exception the auto-configurator throws for the root module of the workspace in some runs, see
 * `PyUvWorkspaceInterpreterTest.drainTheKnownConfiguratorFailure`. It is the sanity check of the configurator
 * itself, `sdkConfigImpl.kt:54`.
 */
private const val NO_INTERPRETER = "has no interpreter in the snapshot after it was configured"

/**
 * A uv workspace is one interpreter for all of its members: the root and every member of `[tool.uv.workspace]` form a
 * single [EvoWorkspace], and an interpreter set on it reaches every member — each one holds the same interpreter and
 * the same interpreter library entry, the one the root owns.
 *
 * A member that keeps its own interpreter, or none, is what this guards against: the packages tool window and the
 * widget would then show one thing for a file of the root and another for a file of a member, and the member would
 * resolve no dependency the workspace installed. The product holds all three cases today, on Linux and on Windows.
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
   * Takes the one exception the auto-configurator throws for this project, so that a run in which it fires is still
   * a verdict on the three cases above and not on a defect they do not cover.
   *
   * It fires on a coroutine of `python.sdkConfigurator.backend`, for the root module of the workspace, and it fires
   * rarely: it was thrown in 1 of 7 CI runs of this class (build 1128368029) and in none of the five repeats of
   * build 1128411011. So nothing here states that it comes, and no tripwire waits for it to stop coming — a claim
   * about an event of that frequency is a claim this class cannot hold. The coverage is the three cases; this only
   * keeps them from going red for a reason of their own.
   *
   * The defect is in `autoConfigureSdk`, `sdkConfigImpl.kt:44-57`: it waits with `EvoPyProjectModel.awaitInterpreterOf`
   * and then reads the snapshot again with `PyProject.getInterpreter`, so what it asserts is not what it waited for.
   * The wait also returns the same way when it times out, and is satisfied at once when the interpreter it expects is
   * `null`.
   *
   * The predicate names this one failure. Any other uncaught exception is left in place and still fails the test.
   *
   * The poll is what makes the drain work at all: the exception reaches the handler after the body of the test is
   * over, so a drain that only looks once takes nothing and leaves it to the extension, see build 1128368029. It is
   * short, because a run in which the exception does not come pays for it. An exception later than this is taken by
   * the drain of the next test, which runs before the extension of that test, or by [drainALateConfiguratorFailure].
   */
  @AfterEach
  fun drainTheKnownConfiguratorFailure(): Unit = timeoutRunBlocking(30.seconds) {
    drainConfiguratorFailure(5.seconds)
  }

  private fun describeWorkspaces(): String =
    EvoPyProjectModel.getInstance(f.project).snapshotOrNull()
      ?.workspaces
      ?.joinToString { "${it.root.pyProject.residesOnModule.name}: ${it.members.map { member -> member.pyProject.residesOnModule.name }}" }
    ?: "no snapshot"

  companion object {
    /** The same drain for an exception thrown after the last test, which no [drainTheKnownConfiguratorFailure] follows. */
    @JvmStatic
    @AfterAll
    fun drainALateConfiguratorFailure(): Unit = timeoutRunBlocking(30.seconds) {
      drainConfiguratorFailure(10.seconds)
    }
  }
}

/** Waits up to [wait] for the exception of the auto-configurator, see `drainTheKnownConfiguratorFailure`, and takes it. */
private suspend fun drainConfiguratorFailure(wait: Duration) {
  withTimeoutOrNull(wait) {
    while (true) {
      if (drainUncaughtExceptions { it is IllegalStateException && it.message?.contains(NO_INTERPRETER) == true }.isNotEmpty()) break
      delay(200.milliseconds)
    }
  }
}
