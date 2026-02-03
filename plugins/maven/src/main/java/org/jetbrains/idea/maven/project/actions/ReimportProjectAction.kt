// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.project.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileEditor.FileDocumentManager
import org.jetbrains.idea.maven.buildtool.MavenSyncSpec
import org.jetbrains.idea.maven.project.MavenProject
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.utils.MavenUtil
import org.jetbrains.idea.maven.utils.actions.MavenActionUtil

/**
 * Sync project
 */
open class ReimportProjectAction : MavenProjectsAction() {
  override fun isVisible(e: AnActionEvent): Boolean {
    if (!super.isVisible(e)) return false
    val context = e.dataContext

    val project = MavenActionUtil.getProject(context)
    if (project == null) return false

    val selectedFiles = MavenActionUtil.getMavenProjectsFiles(context)
    if (selectedFiles.size == 0) return false
    val projectsManager = MavenProjectsManager.getInstance(project)
    for (pomXml in selectedFiles) {
      val mavenProject = projectsManager.findProject(pomXml!!)
      if (mavenProject == null) return false
      if (projectsManager.isIgnored(mavenProject)) return false
    }
    return true
  }

  override fun perform(manager: MavenProjectsManager, mavenProjects: List<MavenProject>, e: AnActionEvent) {
    if (MavenUtil.isProjectTrustedEnoughToImport(manager.project)) {
      FileDocumentManager.getInstance().saveAllDocuments()
      manager.scheduleUpdateMavenProjects(MavenSyncSpec.incremental("ReimportProjectAction", true), mavenProjects.map { it.file }, emptyList())
    }
  }
}