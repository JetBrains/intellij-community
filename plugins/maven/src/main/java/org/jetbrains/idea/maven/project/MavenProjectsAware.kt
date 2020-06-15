// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.project

import com.intellij.openapi.Disposable
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemProjectAware
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemProjectId
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemProjectRefreshListener
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemProjectReloadContext
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemRefreshStatus.SUCCESS
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.observable.properties.AtomicBooleanProperty
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
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
    get() = collectSettingsFiles().map { FileUtil.toCanonicalPath(it) }.toSet()

  override fun subscribe(listener: ExternalSystemProjectRefreshListener, parentDisposable: Disposable) {
    isImportCompleted.afterReset({ listener.beforeProjectRefresh() }, parentDisposable)
    isImportCompleted.afterSet({ listener.afterProjectRefresh(SUCCESS) }, parentDisposable)
  }

  override fun reloadProject(context: ExternalSystemProjectReloadContext) {
    FileDocumentManager.getInstance().saveAllDocuments()
    manager.forceUpdateAllProjectsOrFindAllAvailablePomFiles()
  }

  private fun hasPomFile(rootDirectory: String): Boolean {
    return MavenConstants.POM_NAMES.asSequence()
      .map { join(rootDirectory, it) }
      .any { projectsTree.isPotentialProject(it) }
  }

  private fun collectSettingsFiles() = sequence {
    yieldAll(projectsTree.managedFilesPaths)
    yieldAll(projectsTree.projectsFiles.map { it.path })
    for (mavenProject in projectsTree.projects) {
      ProgressManager.checkCanceled()

      val rootDirectory = mavenProject.directory
      yieldAll(mavenProject.modulePaths)
      yield(join(rootDirectory, MavenConstants.JVM_CONFIG_RELATIVE_PATH))
      yield(join(rootDirectory, MavenConstants.MAVEN_CONFIG_RELATIVE_PATH))
      yield(join(rootDirectory, MavenConstants.MAVEN_WRAPPER_RELATIVE_PATH))
      if (hasPomFile(rootDirectory)) {
        yield(join(rootDirectory, MavenConstants.PROFILES_XML))
      }
    }
  }

  private fun join(parentPath: String, relativePath: String) = File(parentPath, relativePath).path

  init {
    manager.addManagerListener(object : MavenProjectsManager.Listener {
      override fun importAndResolveScheduled() = isImportCompleted.set(false)
      override fun projectImportCompleted() = isImportCompleted.set(true)
    })
  }
}
