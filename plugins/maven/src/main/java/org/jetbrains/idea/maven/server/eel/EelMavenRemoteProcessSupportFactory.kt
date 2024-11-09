// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.server.eel

import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.platform.eel.LocalEelApi
import com.intellij.platform.eel.fs.getPath
import com.intellij.platform.eel.provider.LocalEelKey
import com.intellij.platform.eel.provider.getEelApiBlocking
import com.intellij.platform.eel.provider.getEelApiKey
import org.jetbrains.idea.maven.server.MavenDistribution
import org.jetbrains.idea.maven.server.MavenRemoteProcessSupportFactory
import org.jetbrains.idea.maven.server.MavenRemoteProcessSupportFactory.MavenRemoteProcessSupport
import org.jetbrains.idea.maven.server.RemotePathTransformerFactory
import org.jetbrains.idea.maven.statistics.MavenActionsUsagesCollector
import org.jetbrains.idea.maven.statistics.MavenActionsUsagesCollector.trigger
import kotlin.io.path.Path

class EelMavenRemoteProcessSupportFactory : MavenRemoteProcessSupportFactory {
  override fun create(
    jdk: Sdk,
    vmOptions: String?,
    mavenDistribution: MavenDistribution,
    project: Project,
    debugPort: Int?,
  ): MavenRemoteProcessSupport {
    trigger(project, MavenActionsUsagesCollector.START_WSL_MAVEN_SERVER)
    val eel = project.getEelApiBlocking()
    return EelMavenServerRemoteProcessSupport(eel, jdk, vmOptions, mavenDistribution, project, debugPort)
  }

  override fun isApplicable(project: Project): Boolean {
    // TODO: should we use eel also for local environments?
    return project.getEelApiKey() != LocalEelKey
  }
}

class EelRemotePathTransformFactory : RemotePathTransformerFactory {
  override fun isApplicable(project: Project): Boolean {
    // TODO: should we use eel also for local environments?
    return project.getEelApiKey() != LocalEelKey
  }

  override fun createTransformer(project: Project): RemotePathTransformerFactory.Transformer {
    val eel = project.getEelApiBlocking()

    return object : RemotePathTransformerFactory.Transformer {
      override fun toRemotePath(localPath: String): String {
        if (localPath.isEmpty()) return localPath
        return runCatching { eel.mapper.getOriginalPath(Path(localPath)).toString() }.getOrNull() ?: localPath
      }

      override fun toIdePath(remotePath: String): String {
        if (remotePath.isEmpty()) return remotePath
        return runCatching { eel.mapper.toNioPath(eel.fs.getPath(remotePath)).toString() }.getOrNull() ?: remotePath
      }

      override fun canBeRemotePath(s: String?): Boolean {
        return eel !is LocalEelApi
      }
    }
  }
}