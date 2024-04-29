/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.maven.project.actions

import com.intellij.CommonBundle
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.text.StringUtil
import org.jetbrains.annotations.Nls
import org.jetbrains.idea.maven.project.MavenProject
import org.jetbrains.idea.maven.project.MavenProjectBundle
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.utils.MavenUtil
import org.jetbrains.idea.maven.utils.actions.MavenAction
import org.jetbrains.idea.maven.utils.actions.MavenActionUtil

/**
 * Unlink Maven Projects
 */
class RemoveManagedFilesAction : MavenAction() {
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

      var aggregator = projectsManager.findAggregator(mavenProject)
      while (aggregator != null && !projectsManager.isManagedFile(aggregator.file)) {
        aggregator = projectsManager.findAggregator(aggregator)
      }

      if (aggregator != null && !selectedFiles.contains(aggregator.file)) {
        return false
      }
    }
    return true
  }

  override fun actionPerformed(e: AnActionEvent) {
    val context = e.dataContext

    val project = MavenActionUtil.getProject(context)
    if (project == null) {
      return
    }
    val projectsManager = MavenProjectsManager.getInstance(project)

    val selectedFiles = MavenActionUtil.getMavenProjectsFiles(context)
    projectsManager.removeManagedFiles(selectedFiles, { mavenProject: MavenProject? ->
      checkNotNull(mavenProject)
      var aggregator = projectsManager.findAggregator(mavenProject)
      while (aggregator != null && !projectsManager.isManagedFile(aggregator.file)) {
        aggregator = projectsManager.findAggregator(aggregator)
      }
      if (aggregator != null && !selectedFiles.contains(aggregator.file)) {
        notifyUser(context, mavenProject, aggregator)
      }
    }, { names: List<String> ->
                                         val returnCode =
                                           Messages
                                             .showOkCancelDialog(
                                               ExternalSystemBundle.message("action.detach.external.confirmation.prompt", "Maven",
                                                                            names.size, names),
                                               getActionTitle(names),
                                               CommonBundle.message("button.remove"), CommonBundle.getCancelButtonText(),
                                               Messages.getQuestionIcon())
                                         if (returnCode != Messages.OK) {
                                           return@removeManagedFiles false
                                         }
                                         true
                                       })
  }

  companion object {
    private fun getActionTitle(names: List<String>): @Nls String? {
      return StringUtil.pluralize(ExternalSystemBundle.message("action.detach.external.project.text", "Maven"), names.size)
    }

    private fun notifyUser(context: DataContext, mavenProject: MavenProject, aggregator: MavenProject) {
      val aggregatorDescription = " (" + aggregator.mavenId.displayString + ')'
      val notification =
        Notification(MavenUtil.MAVEN_NOTIFICATION_GROUP, MavenProjectBundle.message("maven.module.remove.failed"),
                     MavenProjectBundle
                       .message("maven.module.remove.failed.explanation", mavenProject.displayName, aggregatorDescription),
                     NotificationType.ERROR
        )

      notification.setImportant(true)
      notification.notify(MavenActionUtil.getProject(context))
    }
  }
}