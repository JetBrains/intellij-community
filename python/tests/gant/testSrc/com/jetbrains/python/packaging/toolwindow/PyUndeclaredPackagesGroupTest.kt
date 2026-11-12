// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.toolwindow

import com.intellij.idea.TestFor
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.fixtures.findPythonInterpreterForTests
import com.jetbrains.python.packaging.PyPackageName
import com.jetbrains.python.packaging.common.PythonPackage
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.packaging.management.RequirementsProviderType
import com.jetbrains.python.packaging.management.TestPackageManagerProvider
import com.jetbrains.python.packaging.packageRequirements.PackageCollectionPackageStructureNode
import com.jetbrains.python.packaging.packageRequirements.PackageStructureNode
import com.jetbrains.python.packaging.packageRequirements.PackageTreeNode
import com.jetbrains.python.packaging.packageRequirements.WorkspaceMemberPackageStructureNode
import com.jetbrains.python.packaging.packageRequirements.newNodeSet
import com.jetbrains.python.packaging.toolwindow.model.UndeclaredPackagesGroup
import com.jetbrains.python.requirements.PythonDependencyTestCase
import org.assertj.core.api.Assertions.assertThat

@TestFor(classes = [UndeclaredPackagesGroup::class])
@Subsystems.Packaging
@Layers.Functional
class PyUndeclaredPackagesGroupTest : PythonDependencyTestCase() {

  fun testTheGroupNamesRequirementsTxt() {
    assertThat(groupLabel(RequirementsProviderType.REQUIREMENTS_TXT, collection())).isEqualTo("Not added to requirements.txt")
  }

  fun testTheGroupNamesPyprojectToml() {
    assertThat(groupLabel(RequirementsProviderType.PYPROJECT_TOML, collection())).isEqualTo("Not added to pyproject.toml")
  }

  // A uv workspace builds the group in a branch of its own.
  fun testTheGroupOfAWorkspaceNamesPyprojectToml() {
    assertThat(groupLabel(RequirementsProviderType.PYPROJECT_TOML, workspace())).isEqualTo("Not added to pyproject.toml")
  }

  fun testTheGroupNamesNoFileWhenTheManagerFindsNone() {
    assertThat(groupLabel(providerType = null, collection())).isEqualTo("Not added to project dependencies")
  }

  private fun collection(): PackageStructureNode =
    PackageCollectionPackageStructureNode(declaredPackages = emptyList(), undeclaredPackages = listOf(undeclaredNode()))

  private fun workspace(): PackageStructureNode =
    WorkspaceMemberPackageStructureNode("app", subMembers = emptyList(), packageTree = null, undeclaredPackages = listOf(undeclaredNode()))

  private fun undeclaredNode(): PackageTreeNode = PackageTreeNode(PyPackageName.from(UNDECLARED))

  private fun groupLabel(providerType: RequirementsProviderType?, tree: PackageStructureNode): String {
    initTestPackageManager(TestPackageManagerProvider())
    if (providerType != null) {
      myFixture.addFileToProject(providerType.filename, "")
      setDependencyRoot(providerType)
    }
    val project = myFixture.project
    val manager = PythonPackageManager.forPythonInterpreter(project, findPythonInterpreterForTests(myFixture.module)!!)
    val index = PyBuilderPackageIndex(installedByName = mapOf(UNDECLARED to PythonPackage(UNDECLARED, "1.0", false)),
                                      outdated = emptyMap())
    val packages = runWithModalProgressBlocking(project, "Building the packages") {
      buildPackages(PyPackageBuildContext(project, manager), tree, index, emptySet(), emptySet(), newNodeSet())
    }
    return packages.filterIsInstance<UndeclaredPackagesGroup>().single().name
  }

  private companion object {
    const val UNDECLARED = "requests"
  }
}
