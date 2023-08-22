// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.server.wsl

import com.intellij.execution.wsl.WSLDistribution
import com.intellij.execution.wsl.WslPath
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import org.jetbrains.idea.maven.server.*
import org.jetbrains.idea.maven.server.MavenRemoteProcessSupportFactory.MavenRemoteProcessSupport
import org.jetbrains.idea.maven.statistics.MavenActionsUsagesCollector
import org.jetbrains.idea.maven.statistics.MavenActionsUsagesCollector.Companion.trigger
import org.jetbrains.idea.maven.utils.MavenLog
import org.jetbrains.idea.maven.utils.MavenWslUtil
import kotlin.io.path.absolutePathString

class WslMavenRemoteProcessSupportFactory : MavenRemoteProcessSupportFactory {
  override fun create(jdk: Sdk,
                      vmOptions: String?,
                      mavenDistribution: MavenDistribution,
                      project: Project,
                      debugPort: Int?): MavenRemoteProcessSupport {
    val wslDistribution = project.basePath?.let { WslPath.getDistributionByWindowsUncPath(it) }
                          ?: throw IllegalArgumentException("Project $project is not WSL based!")
    MavenLog.LOG.info("Use WSL maven distribution at ${mavenDistribution}")
    trigger(project, MavenActionsUsagesCollector.START_WSL_MAVEN_SERVER)
    val wslMavenDistribution = toWslMavenDistribution(mavenDistribution, wslDistribution)
    return WslMavenServerRemoteProcessSupport(wslDistribution, jdk, vmOptions, wslMavenDistribution, project, debugPort)
  }

  private fun toWslMavenDistribution(mavenDistribution: MavenDistribution, wslDistribution: WSLDistribution): WslMavenDistribution {
    if (mavenDistribution is WslMavenDistribution) return mavenDistribution
    if (mavenDistribution is LocalMavenDistribution) {
      return wslDistribution.getWslPath(mavenDistribution.mavenHome.absolutePathString())?.let {
        WslMavenDistribution(wslDistribution, it, it)
      } ?: throw IllegalArgumentException("Cannot use mavenDistribution ${mavenDistribution}")
    }

    throw IllegalArgumentException("Cannot use mavenDistribution ${mavenDistribution}")

  }

  override fun isApplicable(project: Project): Boolean {
    return MavenWslUtil.useWslMaven(project)
  }
}

class WslRemotePathTransformFactory : RemotePathTransformerFactory {
  override fun isApplicable(project: Project): Boolean {
    return MavenWslUtil.useWslMaven(project)
  }

  override fun createTransformer(project: Project): RemotePathTransformerFactory.Transformer {
    val wslDistribution = MavenWslUtil.tryGetWslDistribution(project)
                          ?: throw IllegalArgumentException("Project $project is not WSL based!")
    return object : RemotePathTransformerFactory.Transformer {
      override fun toRemotePath(localPath: String): String? {
        return wslDistribution.getWslPath(localPath)
      }

      override fun toIdePath(remotePath: String): String {
        return wslDistribution.getWindowsPath(remotePath)
      }

      override fun canBeRemotePath(s: String?): Boolean {
        return s?.startsWith("/") ?: false
      }
    }

  }

}