// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.sdk.poetry

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.UserDataHolder
import com.jetbrains.python.sdk.add.PyAddSdkProvider

/**
 * @author vlan
 */

/**
 *  This source code is edited by @koxudaxi Koudai Aono <koxudaxi@gmail.com>
 */

class PyAddPoetrySdkProvider : PyAddSdkProvider {
  override fun createView(project: Project?,
                          module: Module?,
                          newProjectPath: String?,
                          existingSdks: List<Sdk>,
                          context: UserDataHolder) =
    createPoetryPanel(project, module, existingSdks, newProjectPath, context)
}
