// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.poetry.packaging

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.projectRoots.Sdk
import com.jetbrains.python.packaging.PyPackageName
import com.jetbrains.python.packaging.common.PythonPackage
import com.jetbrains.python.packaging.packageRequirements.PackageNode
import com.jetbrains.python.packaging.packageRequirements.PythonPackageRequirementsTreeExtractor
import com.jetbrains.python.packaging.packageRequirements.PythonPackageRequirementsTreeExtractor.Companion.parseTree
import com.jetbrains.python.packaging.packageRequirements.PythonPackageRequirementsTreeExtractorProvider
import com.jetbrains.python.sdk.poetry.isPoetry
import com.jetbrains.python.sdk.poetry.runPoetryWithSdk

/**
 * Extracts package requirements tree using Poetry package manager.
 */
internal class PoetryPackageRequirementsTreeExtractor(private val sdk: Sdk) : PythonPackageRequirementsTreeExtractor {

  override suspend fun extract(pkg: PythonPackage): PackageNode {
    val data = runPoetryWithSdk(sdk, "show", "--tree", pkg.name).getOr {
      thisLogger().info("extracting requirements for package ${pkg.name}: error. Output: \n${it.error}")
      return PackageNode(PyPackageName.from(pkg.name))
    }
    thisLogger().info("extracting requirements for package ${pkg.name}: \n${data.lines()}")
    return parseTree(data.lines())
  }
}

private class PoetryPackageRequirementsTreeExtractorProvider : PythonPackageRequirementsTreeExtractorProvider {
  override fun createExtractor(sdk: Sdk): PythonPackageRequirementsTreeExtractor? =
    if (sdk.isPoetry) PoetryPackageRequirementsTreeExtractor(sdk) else null
}