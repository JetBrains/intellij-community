// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit.alsoWin.pyproject.model

import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.project.modules
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.jps.JpsImportedEntitySource
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.ModuleId
import com.intellij.python.junit5Tests.unit.alsoWin.pyproject.div
import com.intellij.python.pyproject.model.api.cleanupPyProjectBasedModules
import com.intellij.python.pyproject.model.internal.PY_PROJECT_SYSTEM_ID
import com.intellij.python.pyproject.model.internal.workspaceBridge.pyProjectTomlEntity
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.utils.vfs.createDirectory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

/**
 * Tests for [cleanupPyProjectBasedModules] — the "turn off auto-config" path on the Workspace
 * Structure page. Pins the contract: pyproject-sourced modules are deleted, the root module is
 * demoted to plain `.iml` instead of being deleted so the project never ends up module-less,
 * sub-module source and excluded folders relocate to the surviving root, and foreign modules are
 * never touched.
 */
@TestApplication
internal class PyProjectCleanupModulesTest {

  private val f by pyProjectTomlSyncFixture()

  @Test
  fun `cleanup demotes the root pyproject module to plain and deletes sub-modules`(): Unit = timeoutRunBlocking(30.seconds) {
    edtWriteAction { f.root.writePyprojectTomlWithProject("root") }
    edtWriteAction { f.root.createDirectory("sub").writePyprojectTomlWithProject("child") }
    f.reloadProject()

    cleanupPyProjectBasedModules(f.project)

    val snapshot = f.project.workspaceModel.currentSnapshot
    // The sub-module is gone — its name no longer resolves.
    assertThat(snapshot.resolve(ModuleId("child")))
      .describedAs("Sub-module 'child' must be deleted by cleanup")
      .isNull()
    // The root module survives as a plain `.iml` entity.
    val rootModule = snapshot.resolve(ModuleId("root"))
    assertThat(rootModule)
      .describedAs("Root module 'root' must survive cleanup")
      .isNotNull
    assertThat(rootModule!!.isPyProjectSourced())
      .describedAs("Root module entity source must be demoted to plain .iml")
      .isFalse()
    assertThat(rootModule.pyProjectTomlEntity)
      .describedAs("Root module must lose its pyProjectTomlEntity on demotion")
      .isNull()
  }

  @Test
  fun `cleanup is a no-op when no pyproject modules exist`(): Unit = timeoutRunBlocking(30.seconds) {
    // The fixture's two bystander modules (one Java, one Python) are the only entries. No
    // pyproject sync was triggered, so `ourModules` is empty and the function returns before
    // touching any storage.
    val before = f.project.modules.map { it.name }.sorted()

    cleanupPyProjectBasedModules(f.project)

    val after = f.project.modules.map { it.name }.sorted()
    assertThat(after)
      .describedAs("Module set must not change when no pyproject modules exist")
      .isEqualTo(before)
  }

  @Test
  fun `cleanup keeps foreign non-pyproject modules untouched`(): Unit = timeoutRunBlocking(30.seconds) {
    // A sub-pyproject module is created by sync; the fixture's bystanders stay as foreign. If
    // `isPythonEntity` filtering mistakenly included them, the fixture's `tearDown` would also
    // fail, but we assert here explicitly to pin the invariant on the cleanup entry point.
    edtWriteAction { f.root.createDirectory("sub").writePyprojectTomlWithProject("child") }
    f.reloadProject()

    cleanupPyProjectBasedModules(f.project)

    val names = f.project.modules.map { it.name }
    assertThat(names).contains(BYSTANDER_JAVA, BYSTANDER_PYTHON)
  }

  @Test
  fun `cleanup relocates sub-module source roots onto the surviving root`(): Unit = timeoutRunBlocking(30.seconds) {
    // Create a sub-pyproject with its implicit `src/` directory on disk — the cleanup filter now
    // drops URLs that do not exist on disk, so the test must materialize the directory for the
    // relocation to be observable.
    edtWriteAction { f.root.writePyprojectTomlWithProject("root") }
    edtWriteAction {
      val sub = f.root.createDirectory("sub")
      sub.writePyprojectTomlWithProject("child")
      sub.createDirectory("src")
    }
    f.reloadProject()

    cleanupPyProjectBasedModules(f.project)

    // After cleanup, the sub-module is deleted; its `sub/src` source root should live on the
    // root module (the deepest surviving content root that contains `sub/src`).
    val sources = f.sourceRootNames("root")
    assertThat(sources)
      .describedAs("Root module source roots must include the relocated 'sub/src'")
      .contains("sub" / "src")
  }

  private fun ModuleEntity.isPyProjectSourced(): Boolean =
    (entitySource as? JpsImportedEntitySource)?.externalSystemId == PY_PROJECT_SYSTEM_ID.id
}
