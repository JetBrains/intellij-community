// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.project

import com.intellij.openapi.Disposable
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemProjectAware
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemProjectId
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemProjectRefreshListener
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemRefreshStatus
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.PathUtil
import org.jetbrains.idea.maven.model.MavenConstants
import org.jetbrains.idea.maven.utils.MavenUtil
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

class MavenProjectsAware(
  project: Project,
  private val manager: MavenProjectsManager,
  private val projectsTree: MavenProjectsTree,
  private val generalSettings: MavenGeneralSettings,
  private val readingProcessor: MavenProjectsProcessor
) : ExternalSystemProjectAware {

  override val projectId = ExternalSystemProjectId(MavenUtil.SYSTEM_ID, project.name)

  override val settingsFiles: Set<String>
    get() = collectSettingsFiles()

  private val listeners = CopyOnWriteArrayList<ExternalSystemProjectRefreshListener>()

  override fun subscribe(listener: ExternalSystemProjectRefreshListener, parentDisposable: Disposable) {
    listeners.add(listener)
    Disposer.register(parentDisposable, Disposable { listeners.remove(listener) })
  }

  override fun refreshProject() {
    readingProcessor.scheduleTask(MavenProjectsProcessorReadingTask(true, projectsTree, generalSettings) {
      fireStartProjectRefresh()
      manager.scheduleImportAndResolve(true)
        .onSuccess { fireFinishProjectRefresh(ExternalSystemRefreshStatus.SUCCESS) }
        .onError { fireFinishProjectRefresh(ExternalSystemRefreshStatus.FAILURE) }
    })
  }

  private fun isPomFile(path: String): Boolean {
    return MavenUtil.isPotentialPomFile(path) && projectsTree.isPotentialProject(path)
  }

  private fun isProfilesFile(path: String): Boolean {
    if (PathUtil.getFileName(path) != MavenConstants.PROFILES_XML) return false
    val profilesDirectory = path.removeSuffix(MavenConstants.PROFILES_XML)
    val possiblePoms = MavenConstants.POM_NAMES.map { profilesDirectory + it }
    return possiblePoms.any { projectsTree.isPotentialProject(it) }
  }

  private fun collectSettingsFiles(): Set<String> {
    val settingsFiles = ArrayList<String?>()
    settingsFiles.add(generalSettings.effectiveUserSettingsIoFile?.path)
    settingsFiles.add(generalSettings.effectiveGlobalSettingsIoFile?.path)
    for (mavenProject in projectsTree.projects) {
      val rootDirectory = File(mavenProject.directory)
      val jvmConfig = File(rootDirectory, MavenConstants.JVM_CONFIG_RELATIVE_PATH)
      val mavenConfig = File(rootDirectory, MavenConstants.MAVEN_CONFIG_RELATIVE_PATH)
      if (jvmConfig.isFile && jvmConfig.exists()) settingsFiles.add(jvmConfig.path)
      if (mavenConfig.isFile && mavenConfig.exists()) settingsFiles.add(mavenConfig.path)
      val projectFiles = rootDirectory.listFiles()?.map { it.path } ?: emptyList()
      settingsFiles.addAll(projectFiles.filter { isPomFile(it) || isProfilesFile(it) })
    }
    return settingsFiles.mapNotNull { FileUtil.toCanonicalPath(it) }.toSet()
  }

  private fun fireStartProjectRefresh() {
    listeners.forEach { it.beforeProjectRefresh() }
  }

  private fun fireFinishProjectRefresh(status: ExternalSystemRefreshStatus) {
    listeners.forEach { it.afterProjectRefresh(status) }
  }
}
