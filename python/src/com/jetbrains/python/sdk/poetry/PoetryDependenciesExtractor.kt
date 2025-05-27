// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.poetry

import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.Sdk
import com.jetbrains.python.packaging.PythonDependenciesExtractor
import com.jetbrains.python.packaging.PythonDependenciesExtractorProvider
import com.jetbrains.python.packaging.common.PythonPackage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Extractor for top-level dependencies from pyproject.toml for Poetry SDKs.
 */
internal class PoetryDependenciesExtractor(private val sdk: Sdk) : PythonDependenciesExtractor {
  override suspend fun extract(module: Module): List<PythonPackage> {
    val output = runPoetryWithSdk(sdk, "show", "--top-level").getOr { return emptyList() }
    if (output.isBlank()) return emptyList()
    return parsePackageData(output.lines())
  }

  private suspend fun parsePackageData(lines: List<String>): List<PythonPackage> = withContext(Dispatchers.Default) {
    val packageList = mutableListOf<PythonPackage>()

    for (line in lines) {
      val parts = line.split(WHITESPACE_REGEX)
      val packageName = parts[0]
      val version = parts.getOrElse(1) { "" }
      packageList.add(PythonPackage(packageName, version, false))
    }

    packageList
  }
}

private class PoetryDependenciesExtractorProvider : PythonDependenciesExtractorProvider {
  override fun createExtractor(sdk: Sdk): PythonDependenciesExtractor? {
    if (!sdk.isPoetry) return null
    return PoetryDependenciesExtractor(sdk)
  }
}

private val WHITESPACE_REGEX = Regex("\\s+")