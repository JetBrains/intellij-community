// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit.alsoWin.pyproject.model

import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.utils.vfs.createDirectory
import com.intellij.util.concurrency.annotations.RequiresWriteLock
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

/**
 * A `[project]` table without `name` makes `PyProjectToml.parse` return `null`, so the file is dropped from the walk result
 * and becomes indistinguishable from a deleted one. Sync then treats the module built from it as an orphan.
 *
 * A typo in `pyproject.toml` must not cost the user a module: the directory must still belong to some module, even if that
 * module loses its `pyproject.toml` identity and degrades to a plain Python one (PY-91694).
 */
@TestApplication
internal class PyProjectTomlBrokenTomlTest {
  private val f by pyProjectTomlSyncFixture()

  /**
   * PY-91694: breaking the root `pyproject.toml` must leave a module at the project root, otherwise nothing can be
   * launched from it anymore.
   */
  @Test
  fun `broken root pyproject keeps a module at the project root`(): Unit = timeoutRunBlocking(30.seconds) {
    edtWriteAction { f.root.writePyprojectTomlWithProject("root") }

    f.reloadProject()
    f.assertProjectStructure(ExpectedModule("root", contentRoot = "."))

    edtWriteAction { f.root.breakPyprojectToml() }

    f.reloadProject()
    // The pyproject module is gone, but the project root is still a module — a plain one, named after its directory.
    f.assertProjectStructure(ExpectedModule(f.root.name, type = PYTHON, contentRoot = "."))

    // Restore it
    val newProjectName = "root2"
    edtWriteAction { f.root.writePyprojectTomlWithProject(newProjectName) }

    f.reloadProject()

    // The pyproject is here again
    f.assertProjectStructure(ExpectedModule(newProjectName, type = PYPROJECT, contentRoot = "."))
  }


  /**
   * PY-91694: fixing the typo must hand the directory back to the pyproject model under its original name — the module
   * kept alive while the file was broken has to be adopted, not left behind next to a fresh `child@1`.
   */
  @Test
  fun `repairing a broken pyproject restores the pyproject module`(): Unit = timeoutRunBlocking(30.seconds) {
    val sub = edtWriteAction {
      f.root.writePyprojectTomlWithProject("root")
      f.root.createDirectory("sub").also { it.writePyprojectTomlWithProject("child") }
    }

    f.reloadProject()
    edtWriteAction { sub.breakPyprojectToml() }
    f.reloadProject()

    edtWriteAction { sub.writePyprojectTomlWithProject("child") }

    f.reloadProject()
    f.assertProjectStructure(
      ExpectedModule("child", contentRoot = "sub"),
      ExpectedModule("root", contentRoot = "."),
    )
  }
}

/** A `[project]` table that carries everything but the one field the parser insists on. */
private const val PROJECT_SECTION_WITHOUT_NAME: String = "[project]\nversion = \"1.0\""

/** Breaks the `pyproject.toml` of this directory by dropping `name` from its `[project]` section. */
@RequiresWriteLock
private fun VirtualFile.breakPyprojectToml() {
  writePyprojectToml(PROJECT_SECTION_WITHOUT_NAME)
}