// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.pip

import com.jetbrains.python.PyBundle
import com.jetbrains.python.packaging.common.PythonPackageDetails
import com.jetbrains.python.packaging.common.PythonSimplePackageDetails
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.packaging.toolwindow.actions.PythonPackageInstallAction
import com.jetbrains.python.packaging.toolwindow.actions.PythonPackagingToolwindowActionProvider
import com.jetbrains.python.packaging.toolwindow.actions.SimplePythonPackageInstallAction
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
internal class PipPackagingToolwindowActionProvider : PythonPackagingToolwindowActionProvider {
  override fun getInstallActions(details: PythonPackageDetails, packageManager: PythonPackageManager): List<PythonPackageInstallAction>? {
    if (packageManager is PipPythonPackageManager && details is PythonSimplePackageDetails)
      return listOf(SimplePythonPackageInstallAction(PyBundle.message("python.packaging.button.install.package"), packageManager.project))
    return null
  }
}



