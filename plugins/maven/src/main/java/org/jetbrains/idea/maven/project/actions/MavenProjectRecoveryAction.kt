// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project.actions

import com.intellij.ide.actions.cache.AsyncRecoveryResult
import com.intellij.ide.actions.cache.RecoveryScope
import com.intellij.ide.actions.cache.ReopenProjectRecoveryAction
import com.intellij.openapi.util.io.NioFiles
import com.intellij.platform.ide.progress.withBackgroundProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.idea.maven.project.MavenProjectBundle
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.utils.MavenUtil
import kotlin.io.path.exists

class MavenProjectRecoveryAction : ReopenProjectRecoveryAction() {
  override fun canBeApplied(recoveryScope: RecoveryScope): Boolean {
    if (!super.canBeApplied(recoveryScope)) return false
    val project = recoveryScope.project
    val manager = MavenProjectsManager.getInstance(project)
    if (manager.isMavenizedProject) return true
    return manager.projectsTreeFile.exists()
  }

  override suspend fun performAsync(recoveryScope: RecoveryScope): AsyncRecoveryResult {
    val project = recoveryScope.project
    val manager = MavenProjectsManager.getInstance(project)

    val path = manager.projectsTreeFile

    withContext(Dispatchers.IO) {
      withBackgroundProgress(project, MavenProjectBundle.message("maven.project.clean.restart.connectors"), false) {
        MavenUtil.restartMavenConnectors(project, true)
      }
    }

    val projectPath = closeProject(recoveryScope)


    withContext(Dispatchers.IO) {
      withBackgroundProgress(project, MavenProjectBundle.message("maven.project.clean.delete.project.structure.caches"), false) {
        NioFiles.deleteRecursively(path.parent)
      }
    }

    val newRecoveryScope = openProject(projectPath)
    val newManager = MavenProjectsManager.getInstance(newRecoveryScope.project)

    withContext(Dispatchers.IO) {
      newManager.forceUpdateAllProjectsOrFindAllAvailablePomFiles()
    }
    return AsyncRecoveryResult(newRecoveryScope, emptyList())
  }


  override val performanceRate: Int
    get() = 0
  override val presentableName: String
    get() = MavenProjectBundle.message("maven.project.clean.caches")
  override val actionKey: String
    get() = "invalidate-maven"

}
