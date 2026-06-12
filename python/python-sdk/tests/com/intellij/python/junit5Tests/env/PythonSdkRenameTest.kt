// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.env

import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.python.junit5Tests.framework.env.PyEnvTestCase
import com.intellij.python.junit5Tests.framework.env.pySdkFixture
import com.intellij.python.test.env.junit5.pyVenvFixture
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.jetbrains.python.sdk.renameSdk
import com.jetbrains.python.Result
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Renaming an interpreter registered in the [ProjectJdkTable] must rename it in the table and keep every workspace-model reference
 * (the project SDK and any module SDK dependencies) pointing at it (PY-88229: previously these references were left dangling and the
 * interpreter showed up as "No interpreter").
 */
@PyEnvTestCase
class PythonSdkRenameTest {
  private val projectFixture = projectFixture()
  private val moduleAFixture = projectFixture.moduleFixture(tempPathFixture(), addPathToSourceRoot = true)
  private val moduleBFixture = projectFixture.moduleFixture(tempPathFixture(), addPathToSourceRoot = true)

  // A venv registered in the project JDK table but not associated with any module; each test wires up the references it needs.
  private val venvFixture = pySdkFixture().pyVenvFixture(where = tempPathFixture(), addToSdkTable = true)

  // A second, independent venv used to check renaming onto an already used name.
  private val secondVenvFixture = pySdkFixture().pyVenvFixture(where = tempPathFixture(), addToSdkTable = true)

  @Test
  fun renameUpdatesProjectSdkReference(): Unit = runBlocking {
    val project = projectFixture.get()
    val sdk = venvFixture.get()
    val oldName = sdk.name
    val newName = "$oldName renamed"
    edtWriteAction { ProjectRootManager.getInstance(project).projectSdk = sdk }

    edtWriteAction { project.renameSdk(oldName, newName) }

    assertSdkRenamedInTable(oldName, newName)
    assertEquals(newName, ProjectRootManager.getInstance(project).projectSdk?.name, "Project SDK reference must follow the rename")
  }

  @Test
  fun renameUpdatesModuleSdkReferenceWithoutProjectSdk(): Unit = runBlocking {
    val project = projectFixture.get()
    val module = moduleAFixture.get()
    val sdk = venvFixture.get()
    val oldName = sdk.name
    val newName = "$oldName renamed"
    edtWriteAction { ModuleRootModificationUtil.setModuleSdk(module, sdk) }

    edtWriteAction { project.renameSdk(oldName, newName) }

    assertSdkRenamedInTable(oldName, newName)
    assertEquals(newName, ModuleRootManager.getInstance(module).sdk?.name, "Module SDK reference must follow the rename")
    assertNull(ProjectRootManager.getInstance(project).projectSdk, "Project SDK must stay unset")
  }

  @Test
  fun renameUpdatesBothProjectAndModuleReferences(): Unit = runBlocking {
    val project = projectFixture.get()
    val module = moduleAFixture.get()
    val sdk = venvFixture.get()
    val oldName = sdk.name
    val newName = "$oldName renamed"
    edtWriteAction {
      ProjectRootManager.getInstance(project).projectSdk = sdk
      ModuleRootModificationUtil.setModuleSdk(module, sdk)
    }

    edtWriteAction { project.renameSdk(oldName, newName) }

    assertSdkRenamedInTable(oldName, newName)
    assertEquals(newName, ProjectRootManager.getInstance(project).projectSdk?.name, "Project SDK reference must follow the rename")
    assertEquals(newName, ModuleRootManager.getInstance(module).sdk?.name, "Module SDK reference must follow the rename")
  }

  @Test
  fun renameUpdatesAllModulesSharingTheSameSdk(): Unit = runBlocking {
    val project = projectFixture.get()
    val moduleA = moduleAFixture.get()
    val moduleB = moduleBFixture.get()
    val sdk = venvFixture.get()
    val oldName = sdk.name
    val newName = "$oldName renamed"
    edtWriteAction {
      ModuleRootModificationUtil.setModuleSdk(moduleA, sdk)
      ModuleRootModificationUtil.setModuleSdk(moduleB, sdk)
    }

    edtWriteAction { project.renameSdk(oldName, newName) }

    assertSdkRenamedInTable(oldName, newName)
    assertEquals(newName, ModuleRootManager.getInstance(moduleA).sdk?.name, "Module A SDK reference must follow the rename")
    assertEquals(newName, ModuleRootManager.getInstance(moduleB).sdk?.name, "Module B SDK reference must follow the rename")
  }

  @Test
  fun renamingToAnExistingNameDoesNotCreateDuplicate(): Unit = runBlocking {
    val project = projectFixture.get()
    val first = venvFixture.get()
    val second = secondVenvFixture.get()
    val firstName = first.name
    val secondName = second.name

    // SDK names must stay unique in the project JDK table, so renaming the second interpreter to the first's name must be rejected
    // and leave the table untouched.
    val result = edtWriteAction { project.renameSdk(secondName, firstName) }
    assertTrue(result is Result.Failure, "Renaming to an already used name must fail")

    val jdkTable = ProjectJdkTable.getInstance()
    assertEquals(first, jdkTable.findJdk(firstName), "The existing interpreter must keep its name")
    assertEquals(second, jdkTable.findJdk(secondName), "The interpreter being renamed must keep its original name")
  }

  @Test
  fun renamingToTheSameNameIsANoOp(): Unit = runBlocking {
    val project = projectFixture.get()
    val sdk = venvFixture.get()
    val name = sdk.name

    val result = edtWriteAction { project.renameSdk(name, name) }
    assertTrue(result is Result.Success, "Renaming to the same name must be a no-op success")
    assertEquals(sdk, ProjectJdkTable.getInstance().findJdk(name), "The interpreter must be left unchanged")
  }

  @Test
  fun renamingAnUnknownSdkFails(): Unit = runBlocking {
    val project = projectFixture.get()

    val result = edtWriteAction { project.renameSdk("no such interpreter", "another name") }
    assertTrue(result is Result.Failure, "Renaming an interpreter that is not in the project JDK table must fail")
  }

  private fun assertSdkRenamedInTable(oldName: String, newName: String) {
    val jdkTable = ProjectJdkTable.getInstance()
    assertNull(jdkTable.findJdk(oldName), "Old SDK name must be gone from the project JDK table")
    assertEquals(newName, jdkTable.findJdk(newName)?.name, "Renamed SDK must be present under the new name")
  }
}
