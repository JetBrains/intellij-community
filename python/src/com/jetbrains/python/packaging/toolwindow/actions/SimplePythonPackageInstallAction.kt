// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.toolwindow.actions

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.jetbrains.python.packaging.common.PythonRepositoryPackageSpecification
import com.jetbrains.python.packaging.management.toInstallRequest
import com.jetbrains.python.packaging.toolwindow.PyPackagingToolWindowService
import org.jetbrains.annotations.Nls

class SimplePythonPackageInstallAction(text: @Nls String,
                                       project: Project) : PythonPackageInstallAction(text, project) {
  override suspend fun installPackage(specification: PythonRepositoryPackageSpecification) {
    project.service<PyPackagingToolWindowService>().installPackage(specification.toInstallRequest())
  }
}