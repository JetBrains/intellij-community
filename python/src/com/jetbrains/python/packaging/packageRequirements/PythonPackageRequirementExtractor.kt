// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.packageRequirements

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.Sdk
import com.jetbrains.python.packaging.PyPackageName
import com.jetbrains.python.packaging.common.PythonPackage
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface PythonPackageRequirementExtractor {

  suspend fun extract(pkg: PythonPackage, module: Module): List<PyPackageName>

  companion object {
    fun forSdk(sdk: Sdk): PythonPackageRequirementExtractor? =
      PythonPackageRequiresExtractorProvider.EP_NAME.extensionList.firstNotNullOfOrNull { it.createExtractor(sdk) }
  }
}

@ApiStatus.Internal
interface PythonPackageRequiresExtractorProvider {

  fun createExtractor(sdk: Sdk): PythonPackageRequirementExtractor?

  companion object {
    val EP_NAME: ExtensionPointName<PythonPackageRequiresExtractorProvider> = ExtensionPointName.create<PythonPackageRequiresExtractorProvider>("Pythonid.PythonPackageRequiresExtractorProvider")
  }
}