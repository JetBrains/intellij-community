// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.poetry.packaging

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.Sdk
import com.jetbrains.python.packaging.PyPackageName
import com.jetbrains.python.packaging.common.PythonPackage
import com.jetbrains.python.packaging.packageRequirements.PythonPackageRequirementExtractor
import com.jetbrains.python.packaging.packageRequirements.PythonPackageRequiresExtractorProvider
import com.jetbrains.python.sdk.poetry.isPoetry
import com.jetbrains.python.sdk.poetry.runPoetryWithSdk

internal class PoetryPackageRequirementExtractor(private val sdk: Sdk) : PythonPackageRequirementExtractor {

  override suspend fun extract(pkg: PythonPackage, module: Module): List<PyPackageName> {
    val data = runPoetryWithSdk(sdk, "show", pkg.name).getOr {
      thisLogger().info("extracting requires for package ${pkg.name}: error. Output: \n${it.error}")
      return emptyList()
    }
    return parsePackageData(data.lines())
  }

  private fun parsePackageData(lines: List<String>): List<PyPackageName> {
    val packages = mutableListOf<PyPackageName>()

    fun parseDependencyLine(line: String): PyPackageName? {
      val depLine = line.removePrefix(DEPENDENCY_PREFIX).trim()
      val name = depLine.split(" ", limit = 2).let { parts ->
        parts.getOrNull(0)?.trim()
      }
      return name?.let { PyPackageName.from(name) }
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
  override fun createExtractor(sdk: Sdk): PythonPackageRequirementExtractor? {
    if (!sdk.isPoetry) return null
    return PoetryPackageRequirementExtractor(sdk)
  }
}