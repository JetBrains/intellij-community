// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.management

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
interface PythonPackageManagerProvider {

  fun createPackageManagerForSdk(project: Project, sdk: Sdk): PythonPackageManager?

  companion object {
    val EP_NAME = ExtensionPointName.create<PythonPackageManagerProvider>("Pythonid.pythonPackageManagerProvider")
  }
}