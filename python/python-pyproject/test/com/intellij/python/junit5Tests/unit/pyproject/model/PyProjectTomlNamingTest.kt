// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit.pyproject.model

import com.intellij.openapi.application.writeAction
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.writeText
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.jps.entities.ModuleId
import com.intellij.python.pyproject.PY_PROJECT_TOML
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.intellij.testFramework.utils.vfs.createDirectory
import com.intellij.workspaceModel.ide.toPath
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

/**
 * Tests for pyproject.toml module naming: duplicate name resolution with `@N` suffixes,
 * rename propagation, and interaction with non-Python modules.
 */
@TestApplication
internal class PyProjectTomlNamingTest {

  private val tempDirFixture = tempPathFixture()
  private val projectFixture = projectFixture(pathFixture = tempDirFixture)
  private val f by pyProjectTomlSyncFixture(projectFixture, tempDirFixture)

  @Test
  fun `duplicate project names get sequential suffixes`(): Unit = timeoutRunBlocking(30.seconds) {
    writeAction {
      f.root.writePyprojectToml("root")
      f.root.createDirectory("a").writePyprojectToml("dup")
      f.root.createDirectory("b").writePyprojectToml("dup")
      f.root.createDirectory("c").writePyprojectToml("dup")
    }

    f.reloadProject()

    f.assertProjectStructure(
      ExpectedModule("dup", contentRoot = "a"),
      ExpectedModule("dup@1", contentRoot = "b"),
      ExpectedModule("dup@2", contentRoot = "c"),
      ExpectedModule("root", contentRoot = "."),
    )
  }

  @Test
  fun `adding duplicate modules on re-sync does not crash`(): Unit = timeoutRunBlocking(30.seconds) {
    // Initial sync: two modules with unique names
    writeAction {
      f.root.writePyprojectToml("root")
      f.root.createDirectory("first").writePyprojectToml("lib")
    }

    f.reloadProject()
    f.assertProjectStructure(ExpectedModule("lib", contentRoot = "first"), ExpectedModule("root", contentRoot = "."))

    // Add two more modules with the same name as the existing one
    writeAction {
      f.root.createDirectory("second").writePyprojectToml("lib")
      f.root.createDirectory("third").writePyprojectToml("lib")
    }

    f.reloadProject()
    f.assertProjectStructure(
      ExpectedModule("lib", contentRoot = "first"),
      ExpectedModule("lib@1", contentRoot = "second"),
      ExpectedModule("lib@2", contentRoot = "third"),
      ExpectedModule("root", contentRoot = "."),
    )
  }

  @Test
  fun `renaming a module via pyproject name change works`(): Unit = timeoutRunBlocking(30.seconds) {
    writeAction {
      f.root.writePyprojectToml("root")
      f.root.createDirectory("sub").writePyprojectToml("old-name")
    }

    f.reloadProject()
    f.assertProjectStructure(ExpectedModule("old-name", contentRoot = "sub"), ExpectedModule("root", contentRoot = "."))

    // Rename the sub-module via pyproject.toml
    writeAction {
      f.root.findChild("sub")!!.findChild(PY_PROJECT_TOML)!!.writeText("[project]\nname = \"new-name\"")
    }

    f.reloadProject()
    f.assertProjectStructure(ExpectedModule("new-name", contentRoot = "sub"), ExpectedModule("root", contentRoot = "."))
  }

  @Test
  fun `renaming a module to an existing duplicate name gets suffix`(): Unit = timeoutRunBlocking(30.seconds) {
    writeAction {
      f.root.writePyprojectToml("root")
      f.root.createDirectory("a").writePyprojectToml("alpha")
      f.root.createDirectory("b").writePyprojectToml("beta")
    }

    f.reloadProject()
    f.assertProjectStructure(
      ExpectedModule("alpha", contentRoot = "a"),
      ExpectedModule("beta", contentRoot = "b"),
      ExpectedModule("root", contentRoot = "."),
    )

    // Change beta's name to "alpha" — "alpha" is taken, so dir b gets "alpha@1"
    writeAction {
      f.root.findChild("b")!!.findChild(PY_PROJECT_TOML)!!.writeText("[project]\nname = \"alpha\"")
    }

    f.reloadProject()
    f.assertProjectStructure(
      ExpectedModule("alpha", contentRoot = "a"),
      ExpectedModule("alpha@1", contentRoot = "b"),
      ExpectedModule("root", contentRoot = "."),
    )
  }

  @Test
  fun `renaming sub-module to match root module name gets suffix`(): Unit = timeoutRunBlocking(30.seconds) {
    writeAction {
      f.root.writePyprojectToml("pythonproject13")
      f.root.createDirectory("second").writePyprojectToml("pythonproject13x")
    }

    f.reloadProject()
    f.assertProjectStructure(ExpectedModule("pythonproject13", contentRoot = "."), ExpectedModule("pythonproject13x", contentRoot = "second"))

    // Change sub-module to same name as root — "pythonproject13" is taken, gets suffix
    writeAction {
      f.root.findChild("second")!!.findChild(PY_PROJECT_TOML)!!.writeText("[project]\nname = \"pythonproject13\"")
    }

    f.reloadProject()
    f.assertProjectStructure(ExpectedModule("pythonproject13", contentRoot = "."), ExpectedModule("pythonproject13@1", contentRoot = "second"))
  }

  /**
   * Swapping [project].name values (A->B, B->A) gives both modules @1 suffixes
   * because each target name is currently taken by another module.
   */
  @Test
  fun `swapping module names produces suffixes`(): Unit = timeoutRunBlocking(30.seconds) {
    writeAction {
      f.root.writePyprojectToml("root")
      f.root.createDirectory("a").writePyprojectToml("alpha")
      f.root.createDirectory("b").writePyprojectToml("beta")
    }

    f.reloadProject()
    f.assertProjectStructure(
      ExpectedModule("alpha", contentRoot = "a"),
      ExpectedModule("beta", contentRoot = "b"),
      ExpectedModule("root", contentRoot = "."),
    )

    // Swap names — each target is taken, both get @1 suffix
    writeAction {
      f.root.findChild("a")!!.findChild(PY_PROJECT_TOML)!!.writeText("[project]\nname = \"beta\"")
      f.root.findChild("b")!!.findChild(PY_PROJECT_TOML)!!.writeText("[project]\nname = \"alpha\"")
    }

    f.reloadProject()
    f.assertProjectStructure(
      ExpectedModule("alpha@1", contentRoot = "b"),
      ExpectedModule("beta@1", contentRoot = "a"),
      ExpectedModule("root", contentRoot = "."),
    )
  }

  /**
   * Covers assignNames branch #8: an existing `name@N` suffix is dropped when the clean name
   * is no longer contested (no duplicates and not in usedNames).
   *
   * First re-sync after rename keeps the suffix because the old "dup" name is still in allModuleNames.
   * Second re-sync drops the suffix because "dup" is gone from allModuleNames.
   */
  @Test
  fun `suffix dropped when duplicates resolved`(): Unit = timeoutRunBlocking(30.seconds) {
    // Initial sync: root + two entries named "dup"
    writeAction {
      f.root.writePyprojectToml("root")
      f.root.createDirectory("a").writePyprojectToml("dup")
      f.root.createDirectory("b").writePyprojectToml("dup")
    }

    f.reloadProject()
    f.assertProjectStructure(
      ExpectedModule("dup", contentRoot = "a"),
      ExpectedModule("dup@1", contentRoot = "b"),
      ExpectedModule("root", contentRoot = "."),
    )

    // Rename whichever entry got the clean "dup" name to "other"
    val snapshot = f.project.workspaceModel.currentSnapshot
    val dupModule = snapshot.resolve(ModuleId("dup"))!!
    val dupContentRoot = dupModule.contentRoots.single().url.toPath()
    writeAction {
      VirtualFileManager.getInstance().findFileByNioPath(dupContentRoot)!!
        .findChild(PY_PROJECT_TOML)!!.writeText("[project]\nname = \"other\"")
    }

    // First re-sync: suffix kept because "dup" is still in allModuleNames (from old snapshot)
    f.reloadProject()
    f.assertProjectStructure(
      ExpectedModule("dup@1", contentRoot = "b"),
      ExpectedModule("other", contentRoot = "a"),
      ExpectedModule("root", contentRoot = "."),
    )

    // Second re-sync: suffix dropped — "dup" no longer in allModuleNames
    f.reloadProject()
    f.assertProjectStructure(
      ExpectedModule("dup", contentRoot = "b"),
      ExpectedModule("other", contentRoot = "a"),
      ExpectedModule("root", contentRoot = "."),
    )
  }

  /**
   * Covers assignNames branch #3: a new Python entry whose natural name clashes with a
   * pre-existing non-Python module gets an `@N` suffix.
   */
  @Test
  fun `new entry name clashes with non-Python module`(): Unit = timeoutRunBlocking(30.seconds) {
    f.addNonPyprojectModule("lib", "javalib", JAVA)

    // Sync with one pyproject.toml also named "lib"
    writeAction {
      f.root.writePyprojectToml("lib")
    }

    f.reloadProject()

    // Java module keeps "lib", Python module gets "lib@1"
    f.assertProjectStructure(ExpectedModule("lib", type = JAVA, contentRoot = "javalib"), ExpectedModule("lib@1", contentRoot = "."))
  }

  /**
   * Covers assignNames branch #7: an existing `name@N` suffix is preserved when the clean name
   * is contested by a non-Python module (name in usedNames from the Java module).
   */
  @Test
  fun `suffix preserved when name contested by non-Python module`(): Unit = timeoutRunBlocking(30.seconds) {
    f.addNonPyprojectModule("lib", "javalib", JAVA)

    // Initial sync: root + two pyproject entries named "lib"
    writeAction {
      f.root.writePyprojectToml("root")
      f.root.createDirectory("a").writePyprojectToml("lib")
      f.root.createDirectory("b").writePyprojectToml("lib")
    }

    f.reloadProject()
    f.assertProjectStructure(
      ExpectedModule("lib", type = JAVA, contentRoot = "javalib"),
      ExpectedModule("lib@1", contentRoot = "a"),
      ExpectedModule("lib@2", contentRoot = "b"),
      ExpectedModule("root", contentRoot = "."),
    )

    // Remove one pyproject.toml
    writeAction {
      f.root.findChild("b")!!.findChild(PY_PROJECT_TOML)!!.delete(this)
    }

    // Re-sync: suffix preserved on remaining Python module because "lib" is still
    // taken by the Java module (in usedNames from allModuleNames)
    f.reloadProject()
    f.assertProjectStructure(
      ExpectedModule("lib", type = JAVA, contentRoot = "javalib"),
      ExpectedModule("lib@1", contentRoot = "a"),
      ExpectedModule("root", contentRoot = "."),
    )
  }
}
