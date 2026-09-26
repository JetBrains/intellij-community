// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2

import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.python.sdk.ModuleOrProject
import com.jetbrains.python.statistics.modules
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal object PyProjectCreateHelpers {
  suspend fun getModule(
    moduleOrProject: ModuleOrProject?,
    homeFile: VirtualFile?,
  ): Module? {
    val module = when (moduleOrProject) {
      is ModuleOrProject.ModuleAndProject -> moduleOrProject.module
      is ModuleOrProject.ProjectOnly -> {
        homeFile ?: return null
        withContext(Dispatchers.IO) {
          ModuleUtil.findModuleForFile(homeFile, moduleOrProject.project)
        }
      }
      null -> null
    }
    return module ?: moduleOrProject?.project?.modules?.singleOrNull()
  }
}