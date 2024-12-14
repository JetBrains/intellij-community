// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.utils

import com.intellij.openapi.application.writeAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.ex.JavaSdkUtil
import com.intellij.openapi.roots.ProjectRootManager
import java.nio.file.Path

class MavenAsyncUtil {
  companion object {
    suspend fun setupProjectSdk(project: Project, rootDirectory: Path) {
      if (ProjectRootManager.getInstance(project).projectSdk == null) {
        val projectSdk = MavenUtil.suggestProjectSdk(rootDirectory) ?: return
        writeAction {
          JavaSdkUtil.applyJdkToProject(project, projectSdk)
        }
      }
    }
  }
}