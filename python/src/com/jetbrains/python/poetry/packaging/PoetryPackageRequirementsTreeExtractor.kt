// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.poetry.packaging

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.jetbrains.python.packaging.packageRequirements.PackageCollectionPackageStructureNode
import com.jetbrains.python.packaging.packageRequirements.PackageNode
import com.jetbrains.python.packaging.packageRequirements.PackageStructureNode
import com.jetbrains.python.packaging.packageRequirements.PythonPackageRequirementsTreeExtractor
import com.jetbrains.python.packaging.packageRequirements.PythonPackageRequirementsTreeExtractor.Companion.parseTrees
import com.jetbrains.python.packaging.packageRequirements.PythonPackageRequirementsTreeExtractorProvider
import com.jetbrains.python.sdk.poetry.isPoetry
import com.jetbrains.python.sdk.poetry.runPoetryWithSdk

internal class PoetryPackageRequirementsTreeExtractor(private val sdk: Sdk) : PythonPackageRequirementsTreeExtractor {

  override suspend fun extract(declaredPackageNames: Set<String>): PackageStructureNode {
    val allTrees = extractAllPackageTrees()
    val declaredPackages = allTrees.filter { it.name.name in declaredPackageNames }
    val undeclaredPackages = allTrees.filter { it.name.name !in declaredPackageNames }
    return PackageCollectionPackageStructureNode(declaredPackages, undeclaredPackages)
  }

  private suspend fun extractAllPackageTrees(): List<PackageNode> {
    val data = runPoetryWithSdk(sdk, "show", "--tree").getOr {
      thisLogger().info("extracting all package trees: error. Output: \n${it.error}")
      return emptyList()
    }
    return parseTrees(data.lines())
  }
}

internal class PoetryPackageRequirementsTreeExtractorProvider : PythonPackageRequirementsTreeExtractorProvider {
  override fun createExtractor(sdk: Sdk, project: Project): PythonPackageRequirementsTreeExtractor? =
    if (sdk.isPoetry) PoetryPackageRequirementsTreeExtractor(sdk) else null
}
