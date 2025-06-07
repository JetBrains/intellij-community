// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.uv

import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.Sdk
import com.jetbrains.python.packaging.PythonDependenciesExtractor
import com.jetbrains.python.packaging.PythonDependenciesExtractorProvider
import com.jetbrains.python.packaging.common.PythonPackage
import com.jetbrains.python.sdk.basePath
import com.jetbrains.python.sdk.uv.impl.createUvCli
import com.jetbrains.python.sdk.uv.impl.createUvLowLevel
import java.nio.file.Path

/**
 * Extractor for top-level dependencies from pyproject.toml for UV SDKs.
 */
internal class UvDependenciesExtractor(private val uvWorkingDirectory: Path?) : PythonDependenciesExtractor {
  override suspend fun extract(module: Module): List<PythonPackage> {
    val uvWorkingDirectory = uvWorkingDirectory ?: Path.of(module.basePath!!)
    val uv = createUvLowLevel(uvWorkingDirectory, createUvCli())
    return uv.listTopLevelPackages().successOrNull ?: emptyList()
  }
}

private class UvDependenciesExtractorProvider: PythonDependenciesExtractorProvider {
  override fun createExtractor(sdk: Sdk): PythonDependenciesExtractor? {
    val data = sdk.sdkAdditionalData as? UvSdkAdditionalData ?: return null
    return UvDependenciesExtractor(data.uvWorkingDirectory)
  }
}