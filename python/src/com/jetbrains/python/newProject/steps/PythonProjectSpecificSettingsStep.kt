// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.jetbrains.python.newProject.steps

import com.intellij.ide.IdeBundle
import com.intellij.openapi.GitRepositoryInitializer
import com.intellij.openapi.progress.runBackgroundableTask
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile


/**
 * @deprecated Use [com.jetbrains.python.newProjectWizard]
 */
@java.lang.Deprecated(forRemoval = true)
@Deprecated("use com.jetbrains.python.newProjectWizard", level = DeprecationLevel.ERROR)
class PythonProjectSpecificSettingsStep {
  companion object {
    /**
     * @deprecated Use [com.jetbrains.python.newProjectWizard]
     */
    @JvmStatic
    @java.lang.Deprecated(forRemoval = true)
    @Deprecated("use PyV3 in com.jetbrains.python.newProjectWizard or access GitRepositoryInitializer directly",
                level = DeprecationLevel.ERROR)
    fun initializeGit(project: Project, root: VirtualFile) {
      runBackgroundableTask(IdeBundle.message("progress.title.creating.git.repository"), project) {
        GitRepositoryInitializer.getInstance()?.initRepository(project, root, true)
      }
    }
  }
}
