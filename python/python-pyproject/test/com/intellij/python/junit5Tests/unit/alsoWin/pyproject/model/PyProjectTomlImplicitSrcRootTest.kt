// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit.alsoWin.pyproject.model

import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.python.junit5Tests.unit.alsoWin.pyproject.div
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.utils.vfs.createDirectory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

/**
 * The sync marks `<content root>/src` as a source root of every pyproject module, before the directory exists.
 *
 * The auto-import bridge watches only `pyproject.toml` files, because
 * [com.intellij.python.pyproject.model.internal.autoImportBridge.PyExternalSystemProjectAware.settingsFiles] returns
 * the toml paths and nothing else. A new `pyproject.toml` starts a sync, but a new directory does not. The user
 * creates the `src` directory after that sync, so a sync that looks on disk finds nothing (PY-89039).
 *
 * The platform keeps a source root that points to no file in `NonExistingWorkspaceRootsRegistry`, and turns it live
 * as soon as the directory appears. The user therefore needs no second sync and no IDE restart.
 *
 * The project must be open. `WorkspaceModelRootWatcher` serves only `ProjectManager.getInstance().openProjects`, so a
 * closed project never sees the VFS event that turns the source root live.
 */
@TestApplication
internal class PyProjectTomlImplicitSrcRootTest {

  private val f by pyProjectTomlSyncFixture(projectFixture(openAfterCreation = true))

  /** PY-89039: the sync records `sub/src` although the directory does not exist. */
  @Test
  fun `src is a source root before the directory exists`(): Unit = timeoutRunBlocking(30.seconds) {
    edtWriteAction { f.root.createDirectory("sub").writePyprojectTomlWithProject("child") }

    f.reloadProject()

    assertThat(f.root.findFileByRelativePath("sub/src"))
      .describedAs("The test is pointless when `sub/src` exists")
      .isNull()
    f.assertProjectStructure(ExpectedModule("child", contentRoot = "sub", sourceRoots = listOf("sub" / "src")))
  }

  /** PY-89039: the directory becomes a real source root without a second sync. */
  @Test
  fun `a src directory created after the sync becomes a source root`(): Unit = timeoutRunBlocking(30.seconds) {
    val sub = edtWriteAction { f.root.createDirectory("sub").also { it.writePyprojectTomlWithProject("child") } }

    f.reloadProject()

    val src = edtWriteAction { sub.createDirectory("src") }

    // No second reloadProject() on purpose: this is what the user reported.
    readAction {
      assertThat(ProjectFileIndex.getInstance(f.project).isInSourceContent(src))
        .describedAs("`src` must be a source root right after its creation")
        .isTrue()
    }
  }
}
