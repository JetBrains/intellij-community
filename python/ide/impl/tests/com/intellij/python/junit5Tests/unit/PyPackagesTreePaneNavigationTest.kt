// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit

import com.intellij.pycharm.community.ide.impl.configuration.interpreter.PkgTreeRow
import com.intellij.pycharm.community.ide.impl.configuration.interpreter.RowActionabilityInputs
import com.intellij.pycharm.community.ide.impl.configuration.interpreter.classifyPkgRow
import com.intellij.pycharm.community.ide.impl.configuration.interpreter.isActionableRow
import com.intellij.pycharm.community.ide.impl.configuration.interpreter.packageFromTreeComponent
import com.intellij.pycharm.community.ide.impl.configuration.interpreter.rowFromTreeComponent
import com.jetbrains.python.packaging.PyPackageName
import com.jetbrains.python.packaging.packageRequirements.PackageTreeNode
import com.jetbrains.python.packaging.toolwindow.ModuleDepName
import com.jetbrains.python.packaging.toolwindow.WorkspaceMemberName
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import javax.swing.tree.DefaultMutableTreeNode

/**
 * Unit-level coverage for the two testable helpers extracted out of `PyPackagesTreePane` — the
 * Swing `Any?` narrowing (`packageFromTreeComponent`) and the actionability rule (`isActionableRow`).
 * The pane itself still lives on top of a `JTree`, but the decision surface is Swing-free here so
 * a silent shape change or a broken rule is caught by a compile-and-run rather than a smoke click.
 */
class PyPackagesTreePaneNavigationTest {

  //region packageFromTreeComponent

  @Test
  fun `packageFromTreeComponent returns null for a null component`() {
    assertNull(packageFromTreeComponent(null))
  }

  @Test
  fun `packageFromTreeComponent returns null when the component is not a DefaultMutableTreeNode`() {
    assertNull(packageFromTreeComponent("not-a-tree-node"))
  }

  @Test
  fun `packageFromTreeComponent returns null when the user object is not a PackageTreeNode`() {
    val treeNode = DefaultMutableTreeNode("plain-string")
    assertNull(packageFromTreeComponent(treeNode))
  }

  @Test
  fun `packageFromTreeComponent unwraps the payload carried by a PkgTreeRow user object`() {
    val payload = PackageTreeNode(name = PyPackageName.from("requests"), version = "2.32.0")
    val treeNode = DefaultMutableTreeNode(PkgTreeRow.Package(payload))
    assertSame(payload, packageFromTreeComponent(treeNode))
  }

  @Test
  fun `rowFromTreeComponent returns null when the user object is not a PkgTreeRow`() {
    val treeNode = DefaultMutableTreeNode("plain-string")
    assertNull(rowFromTreeComponent(treeNode))
  }

  @Test
  fun `rowFromTreeComponent returns the sealed variant the tree carries`() {
    val payload = PackageTreeNode(name = PyPackageName.from("member-a"), version = null)
    val treeNode = DefaultMutableTreeNode(PkgTreeRow.WorkspaceMember(payload))
    assertEquals(PkgTreeRow.WorkspaceMember(payload), rowFromTreeComponent(treeNode))
  }

  //endregion

  //region isActionableRow

  @Test
  fun `workspace member header itself is not actionable`() {
    val inputs = RowActionabilityInputs(
      nodeName = "member-a",
      parentIsRoot = true,
      parentNodeName = null,
      workspaceMembers = setOf(WorkspaceMemberName("member-a")),
      moduleDeps = emptySet(),
    )
    assertFalse(isActionableRow(inputs))
  }

  @Test
  fun `jps module dep row is not actionable`() {
    val inputs = RowActionabilityInputs(
      nodeName = "sibling-module",
      parentIsRoot = true,
      parentNodeName = null,
      workspaceMembers = emptySet(),
      moduleDeps = setOf(ModuleDepName("sibling-module")),
    )
    assertFalse(isActionableRow(inputs))
  }

  @Test
  fun `top-level declared package is actionable`() {
    val inputs = RowActionabilityInputs(
      nodeName = "requests",
      parentIsRoot = true,
      parentNodeName = null,
      workspaceMembers = emptySet(),
      moduleDeps = emptySet(),
    )
    assertTrue(isActionableRow(inputs))
  }

  @Test
  fun `transitive dependency under an installed package is not actionable`() {
    val inputs = RowActionabilityInputs(
      nodeName = "urllib3",
      parentIsRoot = false,
      parentNodeName = "requests",
      workspaceMembers = emptySet(),
      moduleDeps = emptySet(),
    )
    assertFalse(isActionableRow(inputs))
  }

  @Test
  fun `declared package under a workspace member is actionable`() {
    val inputs = RowActionabilityInputs(
      nodeName = "requests",
      parentIsRoot = false,
      parentNodeName = "member-a",
      workspaceMembers = setOf(WorkspaceMemberName("member-a")),
      moduleDeps = emptySet(),
    )
    assertTrue(isActionableRow(inputs))
  }

  //endregion

  //region classifyPkgRow

  @Test
  fun `classifyPkgRow returns Package when the name matches neither set`() {
    val pkg = PackageTreeNode(name = PyPackageName.from("requests"), version = "2.32.0")
    assertEquals(
      PkgTreeRow.Package(pkg),
      classifyPkgRow(pkg, workspaceMembers = emptySet(), moduleDeps = emptySet()),
    )
  }

  @Test
  fun `classifyPkgRow returns WorkspaceMember when the name matches a workspace member`() {
    val pkg = PackageTreeNode(name = PyPackageName.from("member-a"), version = null)
    assertEquals(
      PkgTreeRow.WorkspaceMember(pkg),
      classifyPkgRow(
        pkg = pkg,
        workspaceMembers = setOf(WorkspaceMemberName("member-a")),
        moduleDeps = emptySet(),
      ),
    )
  }

  @Test
  fun `classifyPkgRow returns ModuleDep when the name matches a jps module dep`() {
    val pkg = PackageTreeNode(name = PyPackageName.from("sibling-module"), version = null)
    assertEquals(
      PkgTreeRow.ModuleDep(pkg),
      classifyPkgRow(
        pkg = pkg,
        workspaceMembers = emptySet(),
        moduleDeps = setOf(ModuleDepName("sibling-module")),
      ),
    )
  }

  @Test
  fun `classifyPkgRow prefers WorkspaceMember when the name appears in both sets`() {
    val pkg = PackageTreeNode(name = PyPackageName.from("shared"), version = null)
    assertEquals(
      PkgTreeRow.WorkspaceMember(pkg),
      classifyPkgRow(
        pkg = pkg,
        workspaceMembers = setOf(WorkspaceMemberName("shared")),
        moduleDeps = setOf(ModuleDepName("shared")),
      ),
    )
  }

  //endregion
}
