// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.server.eel

import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.io.toCanonicalPath
import com.intellij.platform.eel.LocalEelApi
import com.intellij.platform.eel.fs.getPath
import com.intellij.platform.eel.provider.*
import org.jetbrains.idea.maven.server.MavenDistribution
import org.jetbrains.idea.maven.server.MavenRemoteProcessSupportFactory
import org.jetbrains.idea.maven.server.MavenRemoteProcessSupportFactory.MavenRemoteProcessSupport
import org.jetbrains.idea.maven.server.RemotePathTransformerFactory
import org.jetbrains.idea.maven.statistics.MavenActionsUsagesCollector
import org.jetbrains.idea.maven.statistics.MavenActionsUsagesCollector.trigger
import java.nio.file.Paths
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
    val eel = project.getEelDescriptor().upgradeBlocking()
    return EelMavenServerRemoteProcessSupport(eel, jdk, vmOptions, mavenDistribution, project, debugPort)
  }

  override fun isApplicable(project: Project): Boolean {
    // TODO: should we use eel also for local environments?
    return !project.isDefault && project.getEelDescriptor() != LocalEelDescriptor
  }
}

class EelRemotePathTransformFactory : RemotePathTransformerFactory {
  override fun isApplicable(project: Project): Boolean {
    // TODO: should we use eel also for local environments?
    return !project.isDefault && project.getEelDescriptor() != LocalEelDescriptor
  }

  override fun createTransformer(project: Project): RemotePathTransformerFactory.Transformer {
    val eel = project.getEelDescriptor().upgradeBlocking()

    return object : RemotePathTransformerFactory.Transformer {
      override fun toRemotePath(localPath: String): String {
        if (localPath.isEmpty()) return localPath
        return runCatching { Path(localPath).asEelPath().toString() }.getOrNull() ?: localPath
      }

      override fun toIdePath(remotePath: String): String {
        if (remotePath.isEmpty()) return remotePath
        val canonicalPath = Paths.get(remotePath).toCanonicalPath()
        return runCatching {
          val eelPath = eel.fs.getPath(canonicalPath)
          val fullyQualifiedPath = eelPath.asNioPath(project)
          return@runCatching fullyQualifiedPath.toString()
        }.getOrNull() ?: remotePath
      }

      override fun canBeRemotePath(s: String?): Boolean {
        return eel !is LocalEelApi
      }
    }
  }
}