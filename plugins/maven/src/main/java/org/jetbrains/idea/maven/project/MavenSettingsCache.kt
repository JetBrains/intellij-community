// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.TestOnly
import org.jetbrains.idea.maven.utils.MavenEelUtil
import java.nio.file.Path

private data class SettingsHolder(val userSettingsFile: Path, val globalSettingsFile: Path?, val repositoryFile: Path)

@Service(Service.Level.PROJECT)
class MavenSettingsCache(val project: Project) {
  private var holder: SettingsHolder? = null

  @TestOnly
  fun reload() {
    runBlockingMaybeCancellable {
      reloadAsync()
    }
  }

  suspend fun reloadAsync() {
    val settings = MavenWorkspaceSettingsComponent.getInstance(project)
      .settings
      .generalSettings
      .also { it.setProject(project) }

    val userSettings = MavenEelUtil.getUserSettingsAsync(project, settings.userSettingsFile, settings.mavenConfig)
    val globalSettings = MavenEelUtil.getGlobalSettingsAsync(project, settings.mavenHomeType.staticOrBundled(), settings.mavenConfig)
    val localRepo =
      MavenEelUtil.getLocalRepoAsync(project, settings.localRepository, settings.mavenHomeType.staticOrBundled(), userSettings.toString(),
                                     settings.mavenConfig)
    holder = SettingsHolder(userSettings, globalSettings, localRepo)
  }

  private fun holder(): SettingsHolder {
    return holder ?: runBlockingMaybeCancellable {
      reloadAsync()
      holder!!
    }
  }

  fun getEffectiveUserSettingsFile(): Path {
    return holder().userSettingsFile
  }

  fun getEffectiveUserLocalRepo(): Path {
    return holder().repositoryFile
  }


  fun getEffectiveSettingsFiles(): List<Path> {
    return listOf(getEffectiveUserSettingsFile())
  }

  fun getEffectiveVirtualSettingsFiles(): List<VirtualFile> {
    return getEffectiveSettingsFiles().mapNotNull { VfsUtil.findFile(it, false) }
  }

  fun getEffectiveGlobalSettingsFile(): Path? {
    return holder().globalSettingsFile
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): MavenSettingsCache = project.service()
  }
}