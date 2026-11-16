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
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.intellij.workspaceModel.ide.legacyBridge.findModuleEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.withTimeoutOrNull
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

/** The `[project].name` of the root `pyproject.toml`, which is also the name of the module the sync creates for it. */
private const val ROOT = "ws"

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

  private fun describeWorkspaces(): String =
    EvoPyProjectModel.getInstance(f.project).snapshotOrNull()
      ?.workspaces
      ?.joinToString { "${it.root.pyProject.residesOnModule.name}: ${it.members.map { member -> member.pyProject.residesOnModule.name }}" }
    ?: "no snapshot"
}
