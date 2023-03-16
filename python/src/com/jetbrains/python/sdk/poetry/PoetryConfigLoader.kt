// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.poetry


import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.smartReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.jetbrains.python.statistics.sdks


class PoetryConfigLoader : ProjectActivity {
  override suspend fun execute(project: Project) {
    if (ApplicationManager.getApplication().isUnitTestMode) return
    if (project.isDisposed) return
    smartReadAction(project) {
      project.sdks
        .filterNot { it.isPoetry }
        .filter { PoetryConfigService.getInstance(project).poetryVirtualenvPaths.contains(it.homePath) }
        .forEach { it.isPoetry = true }
    }
  }
}
