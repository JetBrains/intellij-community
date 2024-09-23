// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.UserDataHolder

// TODO: Merge into a common Python SDK provider
interface PyAddSdkProvider {
  /**
   * Returns [PyAddSdkView] if applicable.
   */
  fun createView(project: Project?,
                 module: Module?,
                 newProjectPath: String?,
                 existingSdks: List<Sdk>,
                 context: UserDataHolder): PyAddSdkView?

  companion object {
    val EP_NAME: ExtensionPointName<PyAddSdkProvider> = ExtensionPointName.create("Pythonid.pyAddSdkProvider")
  }
}
