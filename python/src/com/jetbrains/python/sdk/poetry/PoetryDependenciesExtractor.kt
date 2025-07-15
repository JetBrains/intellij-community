// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.poetry

import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.jetbrains.python.packaging.PythonDependenciesExtractor
import com.jetbrains.python.packaging.PythonDependenciesExtractorProvider
import com.jetbrains.python.packaging.common.PythonPackage

/**
 * Extractor for top-level dependencies from pyproject.toml for Poetry SDKs.
 */
internal class PoetryDependenciesExtractor(private val sdk: Sdk) : PythonDependenciesExtractor {
  override suspend fun extract(): List<PythonPackage> {
    val output = runPoetryWithSdk(sdk, "show", "--top-level")
      .getOr { return emptyList() }

    if (output.isBlank()) {
      return emptyList()
    }

    return parsePoetryShow(output)
  }
}

private class PoetryDependenciesExtractorProvider : PythonDependenciesExtractorProvider {
  override fun createExtractor(project: Project, sdk: Sdk): PythonDependenciesExtractor? {
    if (!sdk.isPoetry) {
      return null
    }

    return PoetryDependenciesExtractor(sdk)
  }
}