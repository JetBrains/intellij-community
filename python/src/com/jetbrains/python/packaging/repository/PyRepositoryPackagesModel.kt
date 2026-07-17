// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.repository

import com.jetbrains.python.packaging.PyPackageName
import com.jetbrains.python.packaging.common.PythonPackage
import com.jetbrains.python.packaging.packageRequirements.FlatPackageStructureNode
import com.jetbrains.python.packaging.packageRequirements.PackageCollectionPackageStructureNode
import com.jetbrains.python.packaging.packageRequirements.PackageStructureNode
import com.jetbrains.python.packaging.packageRequirements.PackageTreeNode
import com.jetbrains.python.packaging.packageRequirements.WorkspaceMemberPackageStructureNode
import org.jetbrains.annotations.ApiStatus

/**
 * Pure data model for packages belonging to a specific repository.
 * Holds the filtered/resolved tree nodes and a name→package lookup for version resolution.
 * No Swing dependencies — testable independently.
 */
@ApiStatus.Internal
internal data class PyRepositoryPackagesModel(
  val treeNodes: List<PackageTreeNode>,
  val installedByName: Map<PyPackageName, PythonPackage>,
) {
  val count: Int = treeNodes.size

  fun filter(query: String): List<PackageTreeNode> {
    if (query.isEmpty()) return treeNodes
    return treeNodes.filter { it.name.name.contains(query, ignoreCase = true) }
  }

  fun resolveVersion(node: PackageTreeNode): String {
    return installedByName[node.name]?.version ?: node.version ?: ""
  }

  companion object {
    /**
     * Builds a model from a snapshot of installed packages, using only packages present in [repoPackageNames].
     */
    fun fromSnapshot(installedPackages: List<PythonPackage>, repoPackageNames: Set<PyPackageName>): PyRepositoryPackagesModel {
      val installedByName = installedPackages.associateBy { PyPackageName.from(it.name) }
      val nodes = installedPackages
        .filter { PyPackageName.from(it.name) in repoPackageNames }
        .sortedBy { it.name }
        .map { PackageTreeNode(PyPackageName.from(it.name), mutableListOf(), version = it.version) }
      return PyRepositoryPackagesModel(nodes, installedByName)
    }

    /**
     * Builds a model from a full package tree structure and installed packages list,
     * using only packages present in [repoPackageNames].
     * Falls back to flat installed-package list if tree extraction yields no results.
     */
    fun fromPackageTree(
      packageTree: PackageStructureNode,
      installedPackages: List<PythonPackage>,
      repoPackageNames: Set<PyPackageName>,
    ): PyRepositoryPackagesModel {
      val installedByName = installedPackages.associateBy { PyPackageName.from(it.name) }
      val treeNodes = extractTreeNodes(packageTree)
      val filteredNodes = treeNodes
        .filter { it.name in repoPackageNames }
        .sortedBy { it.name.name }

      val finalNodes = filteredNodes.ifEmpty {
        installedPackages
          .filter { PyPackageName.from(it.name) in repoPackageNames }
          .sortedBy { it.name }
          .map { PackageTreeNode(PyPackageName.from(it.name), mutableListOf(), version = it.version) }
      }
      return PyRepositoryPackagesModel(finalNodes, installedByName)
    }

    private fun extractTreeNodes(structure: PackageStructureNode): List<PackageTreeNode> {
      return when (structure) {
        is FlatPackageStructureNode -> emptyList()
        is PackageCollectionPackageStructureNode -> structure.declaredPackages + structure.undeclaredPackages
        is WorkspaceMemberPackageStructureNode -> extractFromWorkspace(structure)
      }
    }

    private fun extractFromWorkspace(member: WorkspaceMemberPackageStructureNode): List<PackageTreeNode> {
      val result = mutableListOf<PackageTreeNode>()
      member.packageTree?.children?.let { result.addAll(it) }
      result.addAll(member.undeclaredPackages)
      for (sub in member.subMembers) {
        result.addAll(extractFromWorkspace(sub))
      }
      return result
    }
  }
}
