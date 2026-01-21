// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.uv.packaging

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.Sdk
import com.jetbrains.python.packaging.PyPackageName
import com.jetbrains.python.packaging.common.PythonPackage
import com.jetbrains.python.packaging.packageRequirements.PythonPackageRequirementExtractor
import com.jetbrains.python.packaging.packageRequirements.PythonPackageRequiresExtractorProvider
import com.jetbrains.python.sdk.add.v2.PathHolder
import com.jetbrains.python.sdk.uv.UvExecutionContext
import com.jetbrains.python.sdk.uv.getUvExecutionContext
import com.jetbrains.python.sdk.uv.uvFlavorData

internal class UvPackageRequirementExtractor(private val sdk: Sdk) : PythonPackageRequirementExtractor {
  override suspend fun extract(pkg: PythonPackage, module: Module): List<PyPackageName> =
    sdk.getUvExecutionContext(module.project)?.let { extractWithContext(it, pkg) } ?: emptyList()

  private suspend fun <P : PathHolder> extractWithContext(context: UvExecutionContext<P>, pkg: PythonPackage): List<PyPackageName> {
    val uv = context.createUvCli().getOr {
      thisLogger().warn("cannot run uv: ${it.error}")
      return emptyList()
    }
    return uv.listPackageRequirements(pkg).getOr {
      thisLogger().info("extracting requires for package ${pkg.name}: error. Output: \n${it.error}")
      return emptyList()
    }
  }
}

internal class UvPackageRequiresExtractorProvider : PythonPackageRequiresExtractorProvider {
  override fun createExtractor(sdk: Sdk): PythonPackageRequirementExtractor? {
    sdk.uvFlavorData ?: return null
    return UvPackageRequirementExtractor(sdk)
  }
}
