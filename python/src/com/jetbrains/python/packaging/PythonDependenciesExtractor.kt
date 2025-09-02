// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.jetbrains.python.packaging.common.PythonPackage
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface PythonDependenciesExtractor {
  suspend fun extract(): List<PythonPackage>

  companion object {
    fun forSdk(project: Project, sdk: Sdk): PythonDependenciesExtractor? =
      PythonDependenciesExtractorProvider.EP_NAME.extensionList.firstNotNullOfOrNull { it.createExtractor(project, sdk) }
  }
}

@ApiStatus.Internal
interface PythonDependenciesExtractorProvider {

  fun createExtractor(project: Project, sdk: Sdk): PythonDependenciesExtractor?

  companion object {
    val EP_NAME: ExtensionPointName<PythonDependenciesExtractorProvider> = ExtensionPointName.create<PythonDependenciesExtractorProvider>("Pythonid.PyProjectDependenciesExtractorProvider")
  }
}