// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.project

import com.intellij.openapi.Disposable
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemProjectAware
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemProjectId
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemProjectRefreshListener
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemRefreshStatus.SUCCESS
import com.intellij.openapi.observable.properties.AtomicBooleanProperty
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.PathUtil
import org.jetbrains.idea.maven.model.MavenConstants
import org.jetbrains.idea.maven.utils.MavenUtil
import java.io.File

class MavenProjectsAware(
  project: Project,
  private val manager: MavenProjectsManager,
  private val projectsTree: MavenProjectsTree
) : ExternalSystemProjectAware {

  private val isImportCompleted = AtomicBooleanProperty(true)

  override val projectId = ExternalSystemProjectId(MavenUtil.SYSTEM_ID, project.name)

  override val settingsFiles: Set<String>
    get() = collectAllSettingsFiles()

  override fun subscribe(listener: ExternalSystemProjectRefreshListener, parentDisposable: Disposable) {
    isImportCompleted.afterReset({ listener.beforeProjectRefresh() }, parentDisposable)
    isImportCompleted.afterSet({ listener.afterProjectRefresh(SUCCESS) }, parentDisposable)
  }

  override fun refreshProject() {
    manager.forceUpdateAllProjectsOrFindAllAvailablePomFiles()
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

  private fun collectAllSettingsFiles(): Set<String> {
    val settingsFiles = ArrayList<String?>()
    settingsFiles.add(manager.generalSettings.effectiveUserSettingsIoFile?.path)
    settingsFiles.add(manager.generalSettings.effectiveGlobalSettingsIoFile?.path)
    settingsFiles.addAll(projectsTree.managedFilesPaths)
    settingsFiles.addAll(projectsTree.projectsFiles.map { it.path })
    settingsFiles.addAll(collectAllProjectSettings())
    return settingsFiles.mapNotNull { FileUtil.toCanonicalPath(it) }.toSet()
  }

  private fun collectAllProjectSettings(): List<String> {
    val settingsFiles = ArrayList<String>()
    for (mavenProject in projectsTree.projects) {
      val rootDirectory = mavenProject.directory
      settingsFiles.addAll(mavenProject.modulePaths)
      settingsFiles.add(File(rootDirectory, MavenConstants.JVM_CONFIG_RELATIVE_PATH).path)
      settingsFiles.add(File(rootDirectory, MavenConstants.MAVEN_CONFIG_RELATIVE_PATH).path)
      settingsFiles.addAll(File(rootDirectory).children.filter { isPomFile(it) || isProfilesFile(it) })
    }
    return settingsFiles
  }

  private val File.children: List<String>
    get() = listFiles()?.map { it.path } ?: emptyList()

  init {
    manager.addManagerListener(object : MavenProjectsManager.Listener {
      override fun importAndResolveScheduled() = isImportCompleted.set(false)
      override fun projectImportCompleted() = isImportCompleted.set(true)
    })
  }
}
