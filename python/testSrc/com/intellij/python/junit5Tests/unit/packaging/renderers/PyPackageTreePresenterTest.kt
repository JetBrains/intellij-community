  // Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit.packaging.renderers

import com.jetbrains.python.packaging.common.PythonPackage
import com.jetbrains.python.packaging.toolwindow.model.DependencyGroupNode
import com.jetbrains.python.packaging.toolwindow.model.InstallablePackage
import com.jetbrains.python.packaging.toolwindow.model.InstalledPackage
import com.jetbrains.python.packaging.toolwindow.model.LoadingNode
import com.jetbrains.python.packaging.toolwindow.model.RequirementPackage
import com.jetbrains.python.packaging.toolwindow.model.UndeclaredPackagesGroup
import com.jetbrains.python.packaging.toolwindow.model.WorkspaceMember
import com.jetbrains.python.packaging.repository.PyPackageRepository
import com.jetbrains.python.packaging.toolwindow.packages.tree.renderers.PyPackageTreePresenter
import com.jetbrains.python.packaging.toolwindow.packages.tree.renderers.asInstalledPackageOrNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import com.jetbrains.python.packaging.toolwindow.packages.tree.renderers.displayablePackageAncestors
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import javax.swing.tree.DefaultMutableTreeNode

internal class PyPackageTreePresenterTest {

  @Test
  fun `no ancestors means not a child of undeclared group`() {
    assertFalse(PyPackageTreePresenter.isChildOfUndeclaredGroup(emptySequence()))
  }

  @Test
  fun `direct child of undeclared group is detected`() {
    val group = UndeclaredPackagesGroup(packages = emptyList())
    val child = installedPackage("requests")

    val root = DefaultMutableTreeNode(null)
    val groupNode = DefaultMutableTreeNode(group)
    val childNode = DefaultMutableTreeNode(child)
    root.add(groupNode)
    groupNode.add(childNode)

    assertTrue(PyPackageTreePresenter.isChildOfUndeclaredGroup(childNode.displayablePackageAncestors()))
  }

  @Test
  fun `nested descendant of undeclared group is detected`() {
    val group = UndeclaredPackagesGroup(packages = emptyList())
    val member = WorkspaceMember("app", packages = emptyList())
    val leaf = installedPackage("urllib3")

    val root = DefaultMutableTreeNode(null)
    val groupNode = DefaultMutableTreeNode(group)
    val memberNode = DefaultMutableTreeNode(member)
    val leafNode = DefaultMutableTreeNode(leaf)
    root.add(groupNode)
    groupNode.add(memberNode)
    memberNode.add(leafNode)

    assertTrue(PyPackageTreePresenter.isChildOfUndeclaredGroup(leafNode.displayablePackageAncestors()))
  }

  @Test
  fun `sibling of undeclared group is not affected`() {
    val group = UndeclaredPackagesGroup(packages = emptyList())
    val declared = installedPackage("numpy")

    val root = DefaultMutableTreeNode(null)
    val groupNode = DefaultMutableTreeNode(group)
    val declaredNode = DefaultMutableTreeNode(declared)
    root.add(groupNode)
    root.add(declaredNode)

    assertFalse(PyPackageTreePresenter.isChildOfUndeclaredGroup(declaredNode.displayablePackageAncestors()))
  }

  @Test
  fun `asInstalledPackageOrNull returns package for InstalledPackage`() {
    val pkg = installedPackage("requests")
    assertSame(pkg, (pkg as com.jetbrains.python.packaging.toolwindow.model.DisplayablePackage).asInstalledPackageOrNull())
  }

  @Test
  fun `asInstalledPackageOrNull returns null for null receiver`() {
    val nullish: com.jetbrains.python.packaging.toolwindow.model.DisplayablePackage? = null
    assertNull(nullish.asInstalledPackageOrNull())
  }

  @Test
  fun `asInstalledPackageOrNull returns null for other variants`() {
    val repo = PyPackageRepository("fake", "https://example.com", null)
    val installable: com.jetbrains.python.packaging.toolwindow.model.DisplayablePackage = InstallablePackage("x", repo)
    val requirement: com.jetbrains.python.packaging.toolwindow.model.DisplayablePackage =
      RequirementPackage(PythonPackage("y", "1.0", false), repo)
    val member: com.jetbrains.python.packaging.toolwindow.model.DisplayablePackage = WorkspaceMember("m", emptyList())
    val loading: com.jetbrains.python.packaging.toolwindow.model.DisplayablePackage = LoadingNode()
    val depGroup: com.jetbrains.python.packaging.toolwindow.model.DisplayablePackage = DependencyGroupNode("dev", emptyList())
    val undeclared: com.jetbrains.python.packaging.toolwindow.model.DisplayablePackage = UndeclaredPackagesGroup(emptyList())

    assertNull(installable.asInstalledPackageOrNull())
    assertNull(requirement.asInstalledPackageOrNull())
    assertNull(member.asInstalledPackageOrNull())
    assertNull(loading.asInstalledPackageOrNull())
    assertNull(depGroup.asInstalledPackageOrNull())
    assertNull(undeclared.asInstalledPackageOrNull())
  }

  @Test
  fun `other group nodes are not treated as undeclared`() {
    val depGroup = DependencyGroupNode("dev", packages = emptyList())
    val child = installedPackage("pytest")

    val root = DefaultMutableTreeNode(null)
    val groupNode = DefaultMutableTreeNode(depGroup)
    val childNode = DefaultMutableTreeNode(child)
    root.add(groupNode)
    groupNode.add(childNode)

    assertFalse(PyPackageTreePresenter.isChildOfUndeclaredGroup(childNode.displayablePackageAncestors()))
  }

  private fun installedPackage(name: String): InstalledPackage =
    InstalledPackage(
      instance = PythonPackage(name, "1.0.0", false),
      repository = null,
      requirements = emptyList(),
    )
}
