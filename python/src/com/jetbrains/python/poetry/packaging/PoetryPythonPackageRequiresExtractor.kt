// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.poetry.packaging

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.Sdk
import com.jetbrains.python.packaging.common.NormalizedPythonPackageName
import com.jetbrains.python.packaging.common.PythonPackage
import com.jetbrains.python.packaging.packageRequires.PythonPackageRequiresExtractor
import com.jetbrains.python.packaging.packageRequires.PythonPackageRequiresExtractorProvider
import com.jetbrains.python.sdk.poetry.isPoetry
import com.jetbrains.python.sdk.poetry.runPoetryWithSdk

internal class PoetryPackageRequiresExtractor(private val sdk: Sdk) : PythonPackageRequiresExtractor {

  override suspend fun extract(pkg: PythonPackage, module: Module): List<NormalizedPythonPackageName> {
    val data = runPoetryWithSdk(sdk, "show", pkg.name).getOr {
      thisLogger().info("extracting requires for package ${pkg.name}: error. Output: \n${it.error}")
      return emptyList()
    }
    return parsePackageData(data.lines())
  }

  private fun parsePackageData(lines: List<String>): List<NormalizedPythonPackageName> {
    val packages = mutableListOf<NormalizedPythonPackageName>()

    fun parseDependencyLine(line: String): NormalizedPythonPackageName? {
      val depLine = line.removePrefix(DEPENDENCY_PREFIX).trim()
      val name = depLine.split(" ", limit = 2).let { parts ->
        parts.getOrNull(0)?.trim()
      }
      return name?.let { NormalizedPythonPackageName.from(name) }
    }

    var inDependenciesSection = false
    for (line in lines) {
      val trimmedLine = line.trim()
      when {
        trimmedLine == DEPENDENCIES_MARKER -> inDependenciesSection = true
        inDependenciesSection && line.startsWith(DEPENDENCY_PREFIX) -> parseDependencyLine(line)?.let { packages.add(it) }
        inDependenciesSection && trimmedLine.isEmpty() -> break
      }
    }

    return packages
  }

  private companion object {
    const val DEPENDENCIES_MARKER = "dependencies"
    const val DEPENDENCY_PREFIX = " - "
  }
}

private class PoetryRequiresExtractorProvider: PythonPackageRequiresExtractorProvider {
  override fun createExtractor(sdk: Sdk): PythonPackageRequiresExtractor? {
    if (!sdk.isPoetry) return null
    return PoetryPackageRequiresExtractor(sdk)
  }
}