// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl.newProjectWizard.impl.emptyProject

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.rootManager
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.pycharm.community.ide.impl.newProjectWizard.welcome.PyWelcome
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.newProjectWizard.PyV3ProjectTypeSpecificSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PyV3EmptyProjectSettings(var generateWelcomeScript: Boolean = false) : PyV3ProjectTypeSpecificSettings {

  override suspend fun generateProject(module: Module, baseDir: VirtualFile, sdk: Sdk): PyResult<Unit> {
    if (!generateWelcomeScript) return Result.success(Unit)

    val sourceRoot = module.rootManager.sourceRoots.firstOrNull() ?: baseDir
    val file = edtWriteAction {
      PyWelcome.prepareFile(module.project, sourceRoot)
    }
    withContext(Dispatchers.EDT) {
      writeIntentReadAction {
        file.navigate(true)
      }
    }

    return Result.success(Unit)
  }

  override fun toString(): String {
    return super.toString() + ",PyV3EmptyProjectRequest(generateWelcomeScript=$generateWelcomeScript)"
  }
}