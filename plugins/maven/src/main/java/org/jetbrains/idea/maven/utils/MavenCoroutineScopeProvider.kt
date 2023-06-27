// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.utils

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope

internal class MavenCoroutineScopeProvider {
  @Service
  private class AppService(val coroutineScope: CoroutineScope)

  @Service(Service.Level.PROJECT)
  private class ProjectService(val coroutineScope: CoroutineScope)

  companion object {
    @JvmStatic
    fun getCoroutineScope(project: Project?): CoroutineScope {
      if (null == project) {
        return ApplicationManager.getApplication().getService(AppService::class.java).coroutineScope
      }
      return project.getService(ProjectService::class.java).coroutineScope
    }
  }
}