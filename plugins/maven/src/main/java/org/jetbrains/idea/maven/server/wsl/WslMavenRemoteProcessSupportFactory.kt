// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.server.wsl

import com.intellij.execution.wsl.WslDistributionManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.project.MavenWorkspaceSettings
import org.jetbrains.idea.maven.project.MavenWorkspaceSettingsComponent
import org.jetbrains.idea.maven.server.MavenDistribution
import org.jetbrains.idea.maven.server.MavenRemoteProcessSupportFactory
import org.jetbrains.idea.maven.server.MavenRemoteProcessSupportFactory.MavenRemoteProcessSupport
import org.jetbrains.idea.maven.server.RemotePathTransformerFactory
import org.jetbrains.idea.maven.server.WslMavenDistribution
import org.jetbrains.idea.maven.utils.MavenWslUtil
import org.jetbrains.idea.maven.utils.MavenWslUtil.getDefaultMavenDistribution
import org.jetbrains.idea.maven.utils.MavenWslUtil.resolveM2Dir

class WslMavenRemoteProcessSupportFactory : MavenRemoteProcessSupportFactory {
  override fun create(jdk: Sdk,
                      vmOptions: String?,
                      mavenDistribution: MavenDistribution?,
                      project: Project,
                      debugPort: Int?): MavenRemoteProcessSupport {
    val wslDistribution = project.basePath?.let { WslDistributionManager.getInstance().distributionFromPath(it) }
                          ?: throw IllegalArgumentException("Project $project is not WSL based!")
    //todo: replace this with settings
    val tempDistribution = wslDistribution.getDefaultMavenDistribution() ?: throw IllegalStateException("Maven is not installed on WSL")
    return WslMavenServerRemoteProcessSupport(wslDistribution, jdk, vmOptions, tempDistribution, project, debugPort)
  }

  override fun isApplicable(project: Project): Boolean {
    return project.basePath?.let(WslDistributionManager::isWslPath) ?: false
  }
}

class WslRemotePathTransformFactory : RemotePathTransformerFactory {
  override fun isApplicable(projectPath: String?): Boolean {
    return projectPath != null && WslDistributionManager.isWslPath(projectPath);
  }

  override fun createTransformer(projectFile: String?): RemotePathTransformerFactory.Transformer {
    val wslDistribution = projectFile?.let { WslDistributionManager.getInstance().distributionFromPath(it) }
                          ?: throw IllegalArgumentException("Project file $projectFile is not WSL based!")
    return object : RemotePathTransformerFactory.Transformer {
      override fun toRemotePath(localPath: String): String? {
        return wslDistribution.getWslPath(localPath)
      }

      override fun toIdePath(remotePath: String): String? {
        return wslDistribution.getWindowsPath(remotePath);
      }
    }

  }

}