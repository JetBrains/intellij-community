// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit.alsoWin.pyproject.model

import com.intellij.idea.TestFor
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.jps.entities.DependencyScope
import com.intellij.platform.workspace.jps.entities.LibraryDependency
import com.intellij.platform.workspace.jps.entities.LibraryId
import com.intellij.platform.workspace.jps.entities.LibraryTableId
import com.intellij.platform.workspace.jps.entities.ModuleDependency
import com.intellij.platform.workspace.jps.entities.ModuleId
import com.intellij.python.junit5Tests.unit.alsoWin.pyproject.div
import com.intellij.python.pyproject.model.internal.workspaceBridge.PY_PROJECT_MODEL_OWNS_MODULES_KEY
import com.intellij.python.pyproject.model.internal.workspaceBridge.PY_PROJECT_MODEL_WRITES_MODULE_DEPENDENCIES_KEY
import com.intellij.python.pyproject.model.internal.workspaceBridge.pyProjectTomlEntity
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.RegistryKeyAppLevel
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.utils.vfs.createDirectory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

/**
 * Outside PyCharm a module at the directory of a `pyproject.toml` belongs to the user, see [ModuleSyncMode.KEEPING]: the
 * model renames nothing, keeps the dependencies of the user, and adds only what the module lacks.
 */
@TestFor(issues = ["PY-94447"])
@TestApplication
@RegistryKeyAppLevel(key = PY_PROJECT_MODEL_OWNS_MODULES_KEY, value = "false")
internal class PyProjectTomlKeptModuleTest {
  private val f by pyProjectTomlSyncFixture()

  private val library = LibraryDependency(LibraryId("user-library", LibraryTableId.ProjectLibraryTableId), false, DependencyScope.COMPILE)

  @Test
  fun `an adopted module keeps its name and the dependencies of the user`(): Unit = timeoutRunBlocking(30.seconds) {
    val dir = f.addNonPyprojectModule("user-module", "user-module", JAVA) {
      dependencies += library
      dependencies += ModuleDependency(ModuleId(BYSTANDER_JAVA), false, DependencyScope.COMPILE, false)
    }
    edtWriteAction { dir.writePyprojectTomlWithProject("toml-name") }

    f.reloadProject()

    val module = f.project.workspaceModel.currentSnapshot.resolve(ModuleId("user-module"))
    assertThat(module).describedAs("The module keeps its name").isNotNull
    assertThat(f.project.workspaceModel.currentSnapshot.resolve(ModuleId("toml-name")))
      .describedAs("The model makes no second module for the directory")
      .isNull()
    assertThat(module!!.pyProjectTomlEntity).describedAs("The module is the one of the pyproject.toml").isNotNull
    assertThat(module.dependencies).describedAs("The dependencies of the user stay").contains(library)
    assertThat(f.writtenModuleDeps("user-module")).containsExactly(BYSTANDER_JAVA)
    assertThat(f.sourceRootNames("user-module")).describedAs("The model adds the implicit source root").contains("user-module" / "src")
  }

  @Test
  fun `a source root that the user removed is not added again`(): Unit = timeoutRunBlocking(30.seconds) {
    edtWriteAction { f.root.createDirectory("sub").writePyprojectTomlWithProject("child") }
    f.reloadProject()
    assertThat(f.sourceRootNames("child")).contains("sub" / "src")

    f.project.workspaceModel.update("the user removes the source root") { storage ->
      val child = storage.resolve(ModuleId("child"))!!
      child.contentRoots.flatMap { it.sourceRoots }.forEach(storage::removeEntity)
    }
    // The first rebuild after the removal must keep the root in the record, or the second one adds it again.
    repeat(2) {
      f.reloadProject()
      assertThat(f.sourceRootNames("child")).describedAs("The removal of the user stays after rebuild ${it + 1}").isEmpty()
    }
  }

  @Test
  fun `with the write of dependencies the model adds a missing module dependency once`(
    @TestDisposable disposable: Disposable,
  ): Unit = timeoutRunBlocking(30.seconds) {
    Registry.get(PY_PROJECT_MODEL_WRITES_MODULE_DEPENDENCIES_KEY).setValue(true, disposable)
    writeAppThatDependsOnLib()
    repeat(2) {
      f.reloadProject()
      assertThat(f.writtenModuleDeps("app")).describedAs("The module dependency after rebuild ${it + 1}").containsExactly("lib")
    }
  }

  @Test
  fun `the model records a dependency and writes no module dependency`(): Unit = timeoutRunBlocking(30.seconds) {
    writeAppThatDependsOnLib()
    f.reloadProject()

    assertThat(f.writtenModuleDeps("app")).describedAs("The module gets no module dependency").isEmpty()
    assertThat(f.tomlDeps("app")).describedAs("The record holds the dependency").containsExactly("lib")
  }

  /** Two `pyproject.toml` files: `app` depends on `lib` by its path. */
  private suspend fun writeAppThatDependsOnLib() {
    edtWriteAction {
      f.root.createDirectory("lib").writePyprojectTomlWithProject("lib")
      f.root.createDirectory("app").writePyprojectToml("""
        [project]
        name = "app"
        dependencies = ["lib"]

        [tool.uv.sources]
        lib = { path = "../lib" }
      """.trimIndent())
    }
  }
}
