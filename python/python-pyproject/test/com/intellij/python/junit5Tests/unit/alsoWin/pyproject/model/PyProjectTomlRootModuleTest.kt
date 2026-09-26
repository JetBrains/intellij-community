// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit.alsoWin.pyproject.model

import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.python.pyproject.PY_PROJECT_TOML
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.utils.vfs.createDirectory
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

/**
 * The project base path is usually the content root of the root module. When the `pyproject.toml` that created this module
 * moves away (or is deleted), the module must stay: otherwise the Project Tree jumps to the new location and no code
 * can be launched from the project root anymore (PY-89569).
 */
@TestApplication
internal class PyProjectTomlRootModuleTest {

  private val f by pyProjectTomlSyncFixture()

  /** Moves the `pyproject.toml` of the project root into a freshly created `sub` directory. */
  private suspend fun movePyprojectTomlToSubDir(): VirtualFile = edtWriteAction {
    val sub = f.root.createDirectory("sub")
    f.root.findChild(PY_PROJECT_TOML)!!.move(this, sub)
    sub
  }

  /**
   * PY-89569: the pyproject module follows its toml file into the subdirectory, and a plain (non-pyproject) module
   * is recreated for the project root.
   */
  @Test
  fun `root pyproject moved to subdirectory keeps a module at the project root`(): Unit = timeoutRunBlocking(30.seconds) {
    edtWriteAction { f.root.writePyprojectTomlWithProject("root") }

    f.reloadProject()
    f.assertProjectStructure(ExpectedModule("root", contentRoot = "."))

    movePyprojectTomlToSubDir()

    f.reloadProject()
    // "root" is still taken by the module being moved when names are assigned, hence the "@1" suffix
    f.assertProjectStructure(
      ExpectedModule("root@1", contentRoot = "sub"),
      ExpectedModule(f.root.name, type = PYTHON, contentRoot = "."),
    )
  }

  /** The root module is created once and only once: repeated syncs must not add a second one nor move it away again. */
  @Test
  fun `module at the project root survives repeated syncs`(): Unit = timeoutRunBlocking(30.seconds) {
    edtWriteAction { f.root.writePyprojectTomlWithProject("root") }
    f.reloadProject()
    movePyprojectTomlToSubDir()
    repeat(10) {
      f.reloadProject()
    }

    // "root" is free again (the recreated module is named after the project directory), so the suffix is dropped
    f.assertProjectStructure(
      ExpectedModule("root", contentRoot = "sub"),
      ExpectedModule(f.root.name, type = PYTHON, contentRoot = "."),
    )
  }


  /** Deleting the root `pyproject.toml` orphans its module, but the project root must remain a module. */
  @Test
  fun `deleting the root pyproject keeps a plain module at the project root`(): Unit = timeoutRunBlocking(30.seconds) {
    edtWriteAction { f.root.writePyprojectTomlWithProject("root") }

    f.reloadProject()
    f.assertProjectStructure(ExpectedModule("root", contentRoot = "."))

    edtWriteAction { f.root.findChild(PY_PROJECT_TOML)!!.delete(this) }

    f.reloadProject()
    f.assertProjectStructure(ExpectedModule(f.root.name, type = PYTHON, contentRoot = "."))
  }

  /** The recreated root module follows the `@N` naming convention when the project directory name is already taken. */
  @Test
  fun `recreated root module gets a suffix when its name is taken`(): Unit = timeoutRunBlocking(30.seconds) {
    f.addNonPyprojectModule(f.root.name, "occupied", JAVA)
    edtWriteAction { f.root.writePyprojectTomlWithProject("root") }

    f.reloadProject()

    edtWriteAction { f.root.findChild(PY_PROJECT_TOML)!!.delete(this) }

    f.reloadProject()
    f.assertProjectStructure(
      ExpectedModule(f.root.name, type = JAVA, contentRoot = "occupied"),
      ExpectedModule("${f.root.name}@1", type = PYTHON, contentRoot = "."),
    )
  }

  /** Nothing is invented for a project root that never had a module: only the subdirectory becomes a module. */
  @Test
  fun `no module is invented for a project root that never had one`(): Unit = timeoutRunBlocking(30.seconds) {
    edtWriteAction { f.root.createDirectory("sub").writePyprojectTomlWithProject("child") }

    f.reloadProject()
    f.assertProjectStructure(ExpectedModule("child", contentRoot = "sub"))
  }
}
