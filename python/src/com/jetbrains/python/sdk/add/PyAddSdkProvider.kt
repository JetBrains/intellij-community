// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.sdk.add

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk

// TODO: Merge into a common Python SDK provider
interface PyAddSdkProvider {
  /**
   * Returns [PyAddSdkView] if applicable.
   */
  fun createView(project: Project?, module: Module?, newProjectPath: String?, existingSdks: List<Sdk>): PyAddSdkView?

  companion object {
    val EP_NAME: ExtensionPointName<PyAddSdkProvider> = ExtensionPointName.create("Pythonid.pyAddSdkProvider")
  }
}