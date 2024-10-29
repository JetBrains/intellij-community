// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.toolwindow.actions

import com.intellij.openapi.extensions.ExtensionPointName
import com.jetbrains.python.packaging.common.PythonPackageDetails
import com.jetbrains.python.packaging.management.PythonPackageManager

interface PythonPackagingToolwindowActionProvider {
  fun getInstallActions(details: PythonPackageDetails, packageManager: PythonPackageManager): List<PythonPackageInstallAction>?

  companion object {
    val EP_NAME = ExtensionPointName.create<PythonPackagingToolwindowActionProvider>("Pythonid.PythonPackagingToolwindowActionProvider")
  }
}