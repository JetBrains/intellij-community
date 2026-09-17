// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.buildtool

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.util.UserDataHolderEx
import com.intellij.openapi.util.getOrCreateUserData
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.project.MavenProjectsTree
import org.jetbrains.idea.maven.project.MavenSettingsCache
import org.jetbrains.idea.maven.utils.MavenUtil
import java.nio.file.Path

@ApiStatus.NonExtendable
@ApiStatus.Experimental
data class MavenSyncSession(val project: Project,
                            val spec: MavenSyncSpec,
                            val projectsTree: MavenProjectsTree) {
  val syncContext: UserDataHolderEx = UserDataHolderBase()

  companion object {
    @JvmStatic
    val TOOLCHAINS_FILE: Key<Path> = Key.create("maven.sync.ToolchainsFile")

    @JvmStatic
    val DISCOVERED_JDK_CACHE_FILE: Key<Path> = Key.create("maven.sync.DiscoveredJdkCacheFile")
  }

}

fun MavenSyncSession.getToolchainsFile(): Path {
  return syncContext.getOrCreateUserData(MavenSyncSession.TOOLCHAINS_FILE) { MavenSettingsCache.getInstance(project).getEffectiveToolchainsFile() }
}

/**
 * The Maven toolchains plugin keeps the discovery cache in the .m2 directory.
 * The location does not depend on the toolchains file location.
 */
fun MavenSyncSession.getDiscoveredJdkCacheFile(): Path {
  return syncContext.getOrCreateUserData(MavenSyncSession.DISCOVERED_JDK_CACHE_FILE) {
    MavenUtil.resolveM2Dir(project).resolve("discovered-jdk-toolchains-cache.xml")
  }
}

fun MavenSyncSession.getBuildToolWindow(): MavenSyncConsole {
  return MavenProjectsManager.getInstance(project).syncConsole
}