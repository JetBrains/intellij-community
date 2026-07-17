// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.poetry

import com.intellij.openapi.util.io.toNioPathOrNull
import com.intellij.python.community.impl.poetry.common.icons.PythonCommunityImplPoetryCommonIcons
import com.jetbrains.python.sdk.PythonSdkAdditionalData
import com.jetbrains.python.sdk.flavors.CPythonSdkFlavor
import com.jetbrains.python.sdk.flavors.PyFlavorData
import com.jetbrains.python.sdk.flavors.PythonFlavorProvider
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path
import javax.swing.Icon


/**
 *  This source code is edited by @koxudaxi Koudai Aono <koxudaxi@gmail.com>
 */

@ApiStatus.Internal
object PyPoetrySdkFlavor : CPythonSdkFlavor<PyFlavorData.Empty>() {
  override fun getIcon(): Icon = PythonCommunityImplPoetryCommonIcons.Poetry
  override fun getFlavorDataClass(): Class<PyFlavorData.Empty> = PyFlavorData.Empty::class.java

  override fun migrateAdditionalData(
    additionalData: PythonSdkAdditionalData,
    data: PyFlavorData.Empty,
  ): AdditionalDataMigration<PyFlavorData.Empty> {
    val workingDirectory = additionalData.associatedModulePath?.takeIf { it.isNotBlank() }?.toNioPathOrNull()
    return AdditionalDataMigration(data, workingDirectory)
  }

  override fun isValidSdkPath(pythonBinaryPath: Path): Boolean = false
}

internal class PyPoetrySdkFlavorProvider : PythonFlavorProvider {
  override fun getFlavor(): PythonSdkFlavor<*> = PyPoetrySdkFlavor
}