// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.toolwindow.actions

import com.intellij.openapi.project.Project
import com.jetbrains.python.packaging.common.PythonRepositoryPackageSpecification
import org.jetbrains.annotations.Nls

abstract class PythonPackageInstallAction(
  internal val text: @Nls String,
  internal val project: Project,
) {

  abstract suspend fun installPackage(specification: PythonRepositoryPackageSpecification)
}