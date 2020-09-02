// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.sdk

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
interface PyConfigureSdkExtension {

  val progressText: String

  /**
   * Try to create SDK for the module and return it or null.
   */
  fun configureSdk(project: Project, module: Module, existingSdks: List<Sdk>): Sdk?

  companion object {
    @JvmField
    val EP_NAME = ExtensionPointName.create<PyConfigureSdkExtension>("Pythonid.configureSdkExtension")
  }
}