// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.poetry.packaging

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.jetbrains.python.packaging.PyPackageName
import com.jetbrains.python.packaging.packageRequirements.PackageCollectionPackageStructureNode
import com.jetbrains.python.packaging.packageRequirements.PackageNode
import com.jetbrains.python.packaging.packageRequirements.PackageStructureNode
import com.jetbrains.python.packaging.packageRequirements.PythonPackageRequirementsTreeExtractor
import com.jetbrains.python.packaging.packageRequirements.PythonPackageRequirementsTreeExtractor.Companion.parseTree
import com.jetbrains.python.packaging.packageRequirements.PythonPackageRequirementsTreeExtractorProvider
import com.jetbrains.python.sdk.poetry.isPoetry
import com.jetbrains.python.sdk.poetry.runPoetryWithSdk

internal class PoetryPackageRequirementsTreeExtractor(private val sdk: Sdk) : PythonPackageRequirementsTreeExtractor {

  override suspend fun extract(declaredPackageNames: Set<String>): PackageStructureNode {
    val declaredPackages = declaredPackageNames.map { extractPackageTree(it) }
    return PackageCollectionPackageStructureNode(declaredPackages, emptyList())
  }

  private suspend fun extractPackageTree(packageName: String): PackageNode {
    val data = runPoetryWithSdk(sdk, "show", "--tree", packageName).getOr {
      thisLogger().info("extracting requirements for package $packageName: error. Output: \n${it.error}")
      return PackageNode(PyPackageName.from(packageName))
    }
    thisLogger().info("extracting requirements for package $packageName: \n${data.lines()}")
    return parseTree(data.lines())
  }
}

internal class PoetryPackageRequirementsTreeExtractorProvider : PythonPackageRequirementsTreeExtractorProvider {
  override fun createExtractor(sdk: Sdk, project: Project): PythonPackageRequirementsTreeExtractor? =
    if (sdk.isPoetry) PoetryPackageRequirementsTreeExtractor(sdk) else null
}
