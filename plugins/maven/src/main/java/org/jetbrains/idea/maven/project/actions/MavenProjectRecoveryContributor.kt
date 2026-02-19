// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project.actions

import com.intellij.ide.actions.cache.RecoveryScope
import com.intellij.openapi.externalSystem.service.project.manage.ExternalSystemRecoveryContributor
import com.intellij.openapi.util.io.NioFiles
import com.intellij.platform.ide.progress.withBackgroundProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.idea.maven.buildtool.MavenSyncSpec
import org.jetbrains.idea.maven.project.MavenProjectBundle
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.project.MavenProjectsManagerEx
import org.jetbrains.idea.maven.utils.MavenUtil
import java.nio.file.Path

@ApiStatus.Internal
class MavenProjectRecoveryContributor : ExternalSystemRecoveryContributor {

  private lateinit var myProjectCacheDir: Path

  override suspend fun beforeClose(recoveryScope: RecoveryScope) {
    val project = recoveryScope.project
    val manager = MavenProjectsManager.getInstance(project)

    myProjectCacheDir = manager.projectCacheDir

    withContext(Dispatchers.IO) {
      withBackgroundProgress(project, MavenProjectBundle.message("maven.project.clean.restart.connectors"), false) {
        MavenUtil.shutdownMavenConnectors(project)
      }
    }
  }

  override suspend fun afterClose() {
    withContext(Dispatchers.IO) {
      NioFiles.deleteRecursively(myProjectCacheDir)
    }
  }

  override suspend fun afterOpen(recoveryScope: RecoveryScope) {
    val newManager = MavenProjectsManager.getInstance(recoveryScope.project) as MavenProjectsManagerEx

    withContext(Dispatchers.IO) {
      newManager.updateAllMavenProjects(MavenSyncSpec.full("MavenProjectRecoveryContributor"))
    }
  }

  class Factory : ExternalSystemRecoveryContributor.Factory {
    override fun createContributor(): ExternalSystemRecoveryContributor {
      return MavenProjectRecoveryContributor()
    }
  }
}
