// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.uv.packaging

import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.python.pyproject.model.api.isPyProjectTomlBased
import com.jetbrains.python.packaging.PyPackageName
import com.jetbrains.python.packaging.common.PythonPackage
import com.jetbrains.python.packaging.packageRequirements.PackageCollectionPackageStructureNode
import com.jetbrains.python.packaging.packageRequirements.PackageNode
import com.jetbrains.python.packaging.packageRequirements.PackageStructureNode
import com.jetbrains.python.packaging.packageRequirements.PythonPackageRequirementsTreeExtractor
import com.jetbrains.python.packaging.packageRequirements.PythonPackageRequirementsTreeExtractor.Companion.parseTrees
import com.jetbrains.python.packaging.packageRequirements.PythonPackageRequirementsTreeExtractorProvider
import com.jetbrains.python.packaging.packageRequirements.WorkspaceMemberPackageStructureNode
import com.jetbrains.python.getOrNull
import com.jetbrains.python.sdk.uv.UvLowLevel
import com.jetbrains.python.sdk.uv.getUvExecutionContext
import com.jetbrains.python.sdk.uv.isUv
import java.nio.file.Path

internal class UvPackageRequirementsTreeExtractor(private val sdk: Sdk, private val project: Project) : PythonPackageRequirementsTreeExtractor {

  override suspend fun extract(declaredPackageNames: Set<String>): PackageStructureNode {
    val uvExecutionContext = sdk.getUvExecutionContext() ?: return PackageCollectionPackageStructureNode(emptyList(), emptyList())
    val uv = uvExecutionContext.createUvCli().getOr { return PackageCollectionPackageStructureNode(emptyList(), emptyList()) }

    val workspaceTree = buildWorkspaceStructure(uv, declaredPackageNames, uvExecutionContext.workingDir)
    if (workspaceTree != null) return workspaceTree

    val declaredPackages = extractDeclaredPackagesFromProjectTree(uv, declaredPackageNames)
    val undeclaredPackages = extractUndeclaredPackages(uv, declaredPackageNames)
    return PackageCollectionPackageStructureNode(declaredPackages, undeclaredPackages)
  }

  private suspend fun extractPackageTree(uv: UvLowLevel<*>, packageName: String): PackageNode {
    val output = uv.listPackageRequirementsTree(PythonPackage(packageName, "", false)).getOr {
      return createLeafNode(packageName)
    }
    return parseTrees(output.lines()).firstOrNull() ?: createLeafNode(packageName)
  }

  /**
   * Extracts declared package trees using a single `uv tree --frozen` call.
   * Falls back to per-package extraction if the project tree call fails.
   */
  private suspend fun extractDeclaredPackagesFromProjectTree(
    uv: UvLowLevel<*>,
    declaredPackageNames: Set<String>,
  ): List<PackageNode> {
    val output = uv.listProjectStructureTree().getOrNull()
      ?: return declaredPackageNames.map { extractPackageTree(uv, it) }
    val projectRoot = parseTrees(output.lines()).firstOrNull()
      ?: return declaredPackageNames.map { extractPackageTree(uv, it) }
    val childrenByName = projectRoot.children.associateBy { it.name.name }
    return declaredPackageNames.map { name -> childrenByName[name] ?: createLeafNode(name) }
  }

  private fun createLeafNode(packageName: String): PackageNode =
    PackageNode(PyPackageName.from(packageName))

  private suspend fun buildWorkspaceStructure(
    uv: UvLowLevel<*>,
    declaredPackageNames: Set<String>,
    uvWorkingDirectory: Path,
  ): WorkspaceMemberPackageStructureNode? {
    val (rootName, subMemberNames) = getWorkspaceLayout(uvWorkingDirectory) ?: return null

    val allMemberNames = (setOf(rootName) + subMemberNames).mapTo(mutableSetOf()) { PyPackageName.from(it).name }

    val rootTree = extractPackageTree(uv, rootName).filterOutMembers(allMemberNames)
    val subMembers = subMemberNames.map { name ->
      WorkspaceMemberPackageStructureNode(name, emptyList(), extractPackageTree(uv, name).filterOutMembers(allMemberNames))
    }

    val shownPackageNames = collectAllPackageNames(rootTree, subMembers)
    val undeclared = extractUndeclaredPackages(uv, declaredPackageNames)
      .filter { it.name.name !in shownPackageNames }

    return WorkspaceMemberPackageStructureNode(rootName, subMembers, rootTree, undeclared)
  }

  private fun PackageNode.filterOutMembers(memberNames: Set<String>): PackageNode {
    val filteredChildren = children
      .filter { it.name.name !in memberNames }
      .map { it.filterOutMembers(memberNames) }
    return PackageNode(name, filteredChildren.toMutableList(), group)
  }

  private fun getWorkspaceLayout(uvWorkingDirectory: Path): Pair<String, List<String>>? {
    val modules = ModuleManager.getInstance(project).modules
      .filter { it.isPyProjectTomlBased }

    var rootName: String? = null
    val subMemberNames = mutableListOf<String>()

    for (module in modules) {
      val moduleDir = ModuleRootManager.getInstance(module).contentRoots.firstOrNull()?.toNioPath() ?: continue
      when {
        moduleDir == uvWorkingDirectory -> rootName = module.name
        moduleDir.startsWith(uvWorkingDirectory) -> subMemberNames.add(module.name)
      }
    }

    if (rootName == null || subMemberNames.isEmpty()) return null
    return rootName to subMemberNames
  }

  private fun collectAllPackageNames(rootTree: PackageNode?, subMembers: List<WorkspaceMemberPackageStructureNode>): Set<String> {
    val names = mutableSetOf<String>()
    rootTree?.let { collectNamesRecursively(it, names) }
    for (member in subMembers) {
      member.packageTree?.let { collectNamesRecursively(it, names) }
    }
    return names
  }

  private fun collectNamesRecursively(node: PackageNode, result: MutableSet<String>) {
    result.add(node.name.name)
    for (child in node.children) {
      collectNamesRecursively(child, result)
    }
  }

  private suspend fun extractUndeclaredPackages(uv: UvLowLevel<*>, declaredPackageNames: Set<String>): List<PackageNode> {
    val output = uv.listAllPackagesTree().getOrNull() ?: return emptyList()
    return parseTrees(output.lines())
      .filter { it.name.name !in declaredPackageNames }
  }
}


internal class UvPackageRequirementsTreeExtractorProvider : PythonPackageRequirementsTreeExtractorProvider {
  override fun createExtractor(sdk: Sdk, project: Project): PythonPackageRequirementsTreeExtractor? {
    if (!sdk.isUv) return null
    return UvPackageRequirementsTreeExtractor(sdk, project)
  }
}
