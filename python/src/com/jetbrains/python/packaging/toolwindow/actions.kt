// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.toolwindow

import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.jetbrains.python.packaging.common.PythonPackageDetails
import com.jetbrains.python.packaging.common.PythonPackageSpecification
import com.jetbrains.python.packaging.management.PythonPackageManager
import org.jetbrains.annotations.Nls

interface PythonPackagingToolwindowActionProvider {
  fun getInstallActions(details: PythonPackageDetails, packageManager: PythonPackageManager): List<PythonPackageInstallAction>?

  companion object {
    val EP_NAME = ExtensionPointName.create<PythonPackagingToolwindowActionProvider>("Pythonid.PythonPackagingToolwindowActionProvider")
  }
}

abstract class PythonPackageInstallAction(internal val text: @Nls String,
                                          internal val project: Project) {

  abstract suspend fun installPackage(specification: PythonPackageSpecification)
}

class SimplePythonPackageInstallAction(text: @Nls String,
                                       project: Project) : PythonPackageInstallAction(text, project) {
  override suspend fun installPackage(specification: PythonPackageSpecification) {
    project.service<PyPackagingToolWindowService>().installPackage(specification)
  }
}