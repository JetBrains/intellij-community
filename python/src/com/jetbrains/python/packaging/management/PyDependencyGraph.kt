// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.management

import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.jetbrains.python.packaging.PyPackageName
import com.jetbrains.python.packaging.packageRequirements.PackageTreeNode
import org.jetbrains.annotations.ApiStatus

/**
 * Installed dependency graph as [PackageTreeNode]s whose [PackageTreeNode.version] is the resolved
 * installed version (not a declared constraint); [PackageTreeNode.children] are empty for flat
 * managers (pip/venv).
 */
@ApiStatus.Internal
@RequiresBackgroundThread
suspend fun PythonPackageManager.installedDependencyGraph(): List<PackageTreeNode> {
  val provider = treeProvider
                 ?: return listInstalledPackages().map { PackageTreeNode(PyPackageName.from(it.name), version = it.version) }
  // `<manager> show --tree` prints version *constraints* for transitive nodes (e.g. `urllib3 >=1.21.1,<1.24`)
  // rather than the resolved installed version, so re-key every node to the concrete installed version.
  val installedVersions = listInstalledPackages().associate {
    PyPackageName.normalizePackageName(it.name) to it.version
  }
  return provider.getDependencyTrees().map { it.withResolvedVersions(installedVersions) }
}

/** Non-blocking flat snapshot (no edges) from the cached list; empty until seeded. */
@ApiStatus.Internal
fun PythonPackageManager.installedDependencyGraphSnapshot(): List<PackageTreeNode> =
  listInstalledPackagesSnapshot().map { PackageTreeNode(PyPackageName.from(it.name), version = it.version) }

private fun PackageTreeNode.withResolvedVersions(installedVersions: Map<String, String>): PackageTreeNode =
  copy(
    version = installedVersions[PyPackageName.normalizePackageName(name.name)] ?: version,
    children = children.mapTo(mutableListOf()) { it.withResolvedVersions(installedVersions) },
  )
