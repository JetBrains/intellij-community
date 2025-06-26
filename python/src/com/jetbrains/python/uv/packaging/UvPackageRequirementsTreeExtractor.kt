// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.uv.packaging

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.Sdk
import com.jetbrains.python.packaging.common.NormalizedPythonPackageName
import com.jetbrains.python.packaging.common.PythonPackage
import com.jetbrains.python.packaging.packageRequirements.PackageNode
import com.jetbrains.python.packaging.packageRequirements.PythonPackageRequirementsTreeExtractor
import com.jetbrains.python.packaging.packageRequirements.PythonPackageRequirementsTreeExtractor.Companion.parseTree
import com.jetbrains.python.packaging.packageRequirements.PythonPackageRequirementsTreeExtractorProvider
import com.jetbrains.python.sdk.basePath
import com.jetbrains.python.sdk.uv.UvSdkAdditionalData
import com.jetbrains.python.sdk.uv.impl.createUvCli
import com.jetbrains.python.sdk.uv.impl.createUvLowLevel
import com.jetbrains.python.sdk.uv.isUv
import java.nio.file.Path

internal class UvPackageRequirementsTreeExtractor(private val uvWorkingDirectory: Path?) : PythonPackageRequirementsTreeExtractor {

  override suspend fun extract(pkg: PythonPackage, module: Module): PackageNode {
    val uvWorkingDirectory = uvWorkingDirectory ?: Path.of(module.basePath!!)
    val uv = createUvLowLevel(uvWorkingDirectory, createUvCli())
    val out = uv.listPackageRequirementsTree(pkg).getOr {
      thisLogger().info("extracting requires for package ${pkg.name}: error. Output: \n${it.error}")
      return PackageNode(NormalizedPythonPackageName.from(pkg.name), mutableListOf())
    }

    return parseTree(out.lines())
  }
}


private class UvPackageRequirementsTreeExtractorProvider : PythonPackageRequirementsTreeExtractorProvider {
  override fun createExtractor(sdk: Sdk): PythonPackageRequirementsTreeExtractor? {
    if (!sdk.isUv) return null
    val data = sdk.sdkAdditionalData as? UvSdkAdditionalData ?: return null
    return UvPackageRequirementsTreeExtractor( data.uvWorkingDirectory)
  }
}
