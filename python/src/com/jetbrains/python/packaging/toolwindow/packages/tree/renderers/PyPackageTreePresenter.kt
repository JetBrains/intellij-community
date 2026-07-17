// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.toolwindow.packages.tree.renderers

import com.jetbrains.python.packaging.toolwindow.model.DependencyGroupNode
import com.jetbrains.python.packaging.toolwindow.model.DisplayablePackage
import com.jetbrains.python.packaging.toolwindow.model.InstallablePackage
import com.jetbrains.python.packaging.toolwindow.model.InstalledPackage
import com.jetbrains.python.packaging.toolwindow.model.LoadingNode
import com.jetbrains.python.packaging.toolwindow.model.RequirementPackage
import com.jetbrains.python.packaging.toolwindow.model.UndeclaredPackagesGroup
import com.jetbrains.python.packaging.toolwindow.model.WorkspaceMember
import javax.swing.tree.DefaultMutableTreeNode

/**
 * Presentation helpers for [PyPackageTreeCellRenderer]. Extracted so tree-shape-derived flags
 * (e.g. "is this package painted as undeclared because it sits under an
 * [UndeclaredPackagesGroup]?") are pure functions covered by unit tests instead of loops embedded
 * in the Swing renderer.
 */
internal object PyPackageTreePresenter {

  /** True when any ancestor of the current tree node is an [UndeclaredPackagesGroup]. */
  fun isChildOfUndeclaredGroup(ancestors: Sequence<DisplayablePackage>): Boolean =
    ancestors.any(::isUndeclaredGroup)

  private fun isUndeclaredGroup(pkg: DisplayablePackage): Boolean = when (pkg) {
    is UndeclaredPackagesGroup -> true
    is InstalledPackage,
    is RequirementPackage,
    is InstallablePackage,
    is WorkspaceMember,
    is DependencyGroupNode,
    is LoadingNode,
      -> false
  }
}

/**
 * Ancestor [DisplayablePackage]s walking upward from the receiver, excluding the receiver itself.
 * Non-package ancestors (e.g. the root) are skipped.
 */
internal fun DefaultMutableTreeNode.displayablePackageAncestors(): Sequence<DisplayablePackage> =
  generateSequence(parent as? DefaultMutableTreeNode) { it.parent as? DefaultMutableTreeNode }
    .mapNotNull { it.userObject as? DisplayablePackage }

/**
 * Narrow a [DisplayablePackage] to an [InstalledPackage], or return `null` for any other variant.
 *
 * Kept as an [InstalledPackage]-specific helper (not a generic `as?`) so the branching stays an
 * exhaustive `when` over the sealed [DisplayablePackage] hierarchy: adding a new variant to
 * [DisplayablePackage] makes this function fail to compile until the author decides how the new
 * kind should behave, instead of silently falling into the `null` branch of an unchecked cast.
 */
internal fun DisplayablePackage?.asInstalledPackageOrNull(): InstalledPackage? = when (this) {
  is InstalledPackage -> this
  is InstallablePackage,
  is RequirementPackage,
  is WorkspaceMember,
  is LoadingNode,
  is DependencyGroupNode,
  is UndeclaredPackagesGroup,
  null,
    -> null
}
