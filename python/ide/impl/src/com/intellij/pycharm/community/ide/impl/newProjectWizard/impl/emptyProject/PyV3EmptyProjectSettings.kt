// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl.newProjectWizard.impl.emptyProject

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.python.newProjectWizard.PyV3ProjectTypeSpecificSettings
import com.intellij.pycharm.community.ide.impl.newProjectWizard.welcome.PyWelcome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PyV3EmptyProjectSettings(var generateWelcomeScript: Boolean = false) : PyV3ProjectTypeSpecificSettings {

  override suspend fun generateProject(module: Module, baseDir: VirtualFile, sdk: Sdk): Result<Unit> {
    if (!generateWelcomeScript) return Result.success(Unit)
    val file = writeAction {
      PyWelcome.prepareFile(module.project, baseDir)
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