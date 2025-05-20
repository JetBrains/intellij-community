// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.uv.packaging

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.Sdk
import com.jetbrains.python.packaging.common.NormalizedPythonPackageName
import com.jetbrains.python.packaging.common.PythonPackage
import com.jetbrains.python.packaging.packageRequires.PythonPackageRequiresExtractor
import com.jetbrains.python.packaging.packageRequires.PythonPackageRequiresExtractorProvider
import com.jetbrains.python.sdk.basePath
import com.jetbrains.python.sdk.uv.UvSdkAdditionalData
import com.jetbrains.python.sdk.uv.impl.createUvCli
import com.jetbrains.python.sdk.uv.impl.createUvLowLevel
import java.nio.file.Path

internal class UvPackageRequiresExtractor(private val uvWorkingDirectory: Path?) : PythonPackageRequiresExtractor {
  override suspend fun extract(pkg: PythonPackage, module: Module): List<NormalizedPythonPackageName> {
    val uvWorkingDirectory = uvWorkingDirectory ?: Path.of(module.basePath!!)
    val uv = createUvLowLevel(uvWorkingDirectory, createUvCli())
    return uv.listPackageRequirements(pkg).getOr {
      thisLogger().info("extracting requires for package ${pkg.name}: error. Output: \n${it.error}")
      return emptyList()
    }
  }
}

private class UvPackageRequiresExtractorProvider: PythonPackageRequiresExtractorProvider {
  override fun createExtractor(sdk: Sdk): PythonPackageRequiresExtractor? {
    val data = sdk.sdkAdditionalData as? UvSdkAdditionalData ?: return null
    return UvPackageRequiresExtractor(data.uvWorkingDirectory)
  }
}