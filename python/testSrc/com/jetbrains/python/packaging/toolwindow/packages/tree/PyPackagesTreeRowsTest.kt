// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.toolwindow.packages.tree

import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.packaging.common.PythonPackage
import com.jetbrains.python.packaging.repository.PyPiPackageRepository
import com.jetbrains.python.packaging.toolwindow.model.DisplayablePackage
import com.jetbrains.python.packaging.toolwindow.model.InstalledPackage
import com.jetbrains.python.packaging.toolwindow.model.RequirementPackage
import com.jetbrains.python.packaging.toolwindow.model.WorkspaceMember
import com.jetbrains.python.packaging.toolwindow.packages.tree.renderers.installedFromTooltip
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.net.URI
import java.nio.file.Path
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode

/**
 * The rows the packages view builds from a dependency graph that shares a package between its
 * dependents and can have a cycle (PY-90174).
 */
@Subsystems.PackagingRequirements
@Layers.Functional
class PyPackagesTreeRowsTest {

  @Test
  fun `a package keeps its dependencies wherever it appears`() {
    // `celery` is a dependency of two packages. Both rows must list what it depends on.
    val kombu = pkg("kombu")
    val celery = pkg("celery", kombu)
    val root = pkg("myapp", pkg("airflow", celery), pkg("flower", celery))

    val rows = root.toTreeNode()

    assertThat(rows.childNames()).containsExactly("airflow", "flower")
    for (parent in 0..1) {
      val celeryRow = rows.childAt(parent).childAt(0)
      assertThat(celeryRow.name()).isEqualTo("celery")
      assertThat(celeryRow.childNames()).describedAs("dependencies under parent $parent").containsExactly("kombu")
    }
  }

  @Test
  fun `a package repeating on its own path is a leaf`() {
    val a = pkg("a")
    val b = pkg("b", a)
    a.dependsOn(b)

    val rows = a.toTreeNode()

    // a -> b -> a, and there the walk ends.
    val repeated = rows.childAt(0).childAt(0)
    assertThat(repeated.name()).isEqualTo("a")
    assertThat(repeated.childCount).isEqualTo(0)
  }

  @Test
  fun `a self dependency is a leaf`() {
    val a = pkg("a")
    a.dependsOn(a)

    val rows = a.toTreeNode()

    assertThat(rows.childAt(0).name()).isEqualTo("a")
    assertThat(rows.childAt(0).childCount).isEqualTo(0)
  }

  private fun pkg(name: String, vararg requirements: RequirementPackage): RequirementPackage =
    RequirementPackage(PythonPackage(name, "1.0", false), PyPiPackageRepository, mutableListOf(*requirements))

  /** The graph can have a cycle, which only a mutable dependency list can express. */
  private fun RequirementPackage.dependsOn(other: RequirementPackage) {
    @Suppress("UNCHECKED_CAST")
    (getRequirements() as MutableList<RequirementPackage>).add(other)
  }

  private fun DefaultMutableTreeNode.childAt(index: Int) = getChildAt(index) as DefaultMutableTreeNode
  private fun DefaultMutableTreeNode.name() = (userObject as DisplayablePackage).name
  private fun DefaultMutableTreeNode.childNames() = children().toList().map { (it as DefaultMutableTreeNode).name() }
}

/**
 * [expandAllRows] must reach the rows that expanding itself adds, which a bound taken before the
 * first expansion does not (PY-90174).
 */
@Subsystems.PackagingRequirements
@Layers.Functional
class PyPackagesTreeExpandTest {

  @Test
  fun `every row is expanded, including the ones expanding adds`() {
    val tree = JTree(chain())
    tree.isRootVisible = true
    tree.collapseRow(0)

    tree.expandAllRows()

    // A chain of five: every node is visible only once every row above it is expanded.
    assertThat(tree.rowCount).isEqualTo(CHAIN_DEPTH)
  }

  @Test
  fun `a wide tree is expanded on every branch`() {
    val root = DefaultMutableTreeNode("root")
    repeat(3) { branch ->
      val node = DefaultMutableTreeNode("branch-$branch")
      node.add(DefaultMutableTreeNode("leaf-$branch"))
      root.add(node)
    }
    val tree = JTree(root)
    tree.isRootVisible = true

    tree.expandAllRows()

    assertThat(tree.rowCount).isEqualTo(7)
  }

  /** Five nodes, each the only child of the one above, so each row appears only after the last is expanded. */
  private fun chain(): DefaultMutableTreeNode {
    val nodes = (0 until CHAIN_DEPTH).map { DefaultMutableTreeNode("node-$it") }
    nodes.zipWithNext { parent, child -> parent.add(child) }
    return nodes.first()
  }

  private companion object {
    private const val CHAIN_DEPTH: Int = 5
  }
}

/**
 * The "installed from" tooltip belongs to the tree, not to the renderer: a renderer that sets it
 * registers itself with `ToolTipManager`, and the one reused renderer instance sits outside the
 * rows, so the tooltip was placed away from the row the mouse is on (PY-90174).
 */
@Subsystems.PackagingRequirements
@Layers.Functional
class PyPackagesInstalledFromTooltipTest {

  @Test
  fun `a package from an index has no tooltip`() {
    assertThat(pkg("requests").installedFromTooltip()).isNull()
  }

  @Test
  fun `an editable local install names its path`() {
    val edge3 = pkg("edge3", editable = true, location = URI("file:///airflow/providers/edge3"))

    assertThat(edge3.installedFromTooltip()).contains(Path.of("/airflow/providers/edge3").toString())
  }

  @Test
  fun `a local install names its path`() {
    val edge3 = pkg("edge3", location = URI("file:///airflow/providers/edge3"))

    assertThat(edge3.installedFromTooltip()).contains(Path.of("/airflow/providers/edge3").toString())
  }

  @Test
  fun `an editable install without a location still has a tooltip`() {
    assertThat(pkg("edge3", editable = true).installedFromTooltip()).isNotNull()
  }

  @Test
  fun `every row type names the path in the same words`() {
    val instance = pkg("edge3", editable = true, location = URI("file:///airflow/providers/edge3"))
    val rows = listOf(
      InstalledPackage(instance, PyPiPackageRepository, requirements = emptyList()),
      RequirementPackage(instance, PyPiPackageRepository),
      WorkspaceMember("edge3", emptyList(), instance),
    )

    assertThat(rows.map { it.rowTooltip() }.distinct()).hasSize(1)
    assertThat(rows.first().rowTooltip()).contains(Path.of("/airflow/providers/edge3").toString())
  }

  @Test
  fun `a row that stands for no package has none`() {
    assertThat(WorkspaceMember("edge3", emptyList()).rowTooltip()).isNull()
  }

  private fun pkg(name: String, editable: Boolean = false, location: URI? = null): PythonPackage =
    PythonPackage(name, "1.0", editable).apply { editableLocation = location }
}
