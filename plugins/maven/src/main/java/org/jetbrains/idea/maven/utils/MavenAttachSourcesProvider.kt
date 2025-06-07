// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.utils

import com.intellij.codeInsight.AttachSourcesProvider
import com.intellij.codeInsight.AttachSourcesProvider.AttachSourcesAction
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.ActionCallback
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.psi.PsiFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.idea.maven.importing.MavenExtraArtifactType
import org.jetbrains.idea.maven.importing.MavenRootModelAdapter
import org.jetbrains.idea.maven.model.MavenArtifact
import org.jetbrains.idea.maven.model.MavenId
import org.jetbrains.idea.maven.project.MavenProject
import org.jetbrains.idea.maven.project.MavenProjectBundle
import org.jetbrains.idea.maven.project.MavenProjectsManager
import java.io.IOException
import java.nio.file.Files

internal class MavenAttachSourcesProvider : AttachSourcesProvider {

  @Service(Service.Level.PROJECT)
  private class CoroutineService(val coroutineScope: CoroutineScope)

  override fun getActions(orderEntries: List<LibraryOrderEntry>,
                          psiFile: PsiFile): Collection<AttachSourcesAction> {
    val projects = getMavenProjects(psiFile)
    if (projects.isEmpty()) return listOf()
    return if (findArtifacts(projects, orderEntries).isEmpty()) listOf()
    else listOf(object : AttachSourcesAction {
      override fun getName() = MavenProjectBundle.message("maven.action.download.sources")
      override fun getBusyText() = MavenProjectBundle.message("maven.action.download.sources.busy.text")

      override fun perform(orderEntriesContainingFile: List<LibraryOrderEntry>): ActionCallback {
        val project = psiFile.getProject()
        val cs = project.service<CoroutineService>().coroutineScope
        val resultWrapper = ActionCallback()
        cs.launch {
          // may have been changed by this time...
          val mavenProjects = readAction { getMavenProjects(psiFile) }
          if (mavenProjects.isEmpty()) {
            resultWrapper.setRejected()
            return@launch
          }

          val manager = MavenProjectsManager.getInstance(project)
          val artifacts = findArtifacts(mavenProjects, orderEntries)
          if (artifacts.isEmpty()) {
            resultWrapper.setRejected()
            return@launch
          }

          val downloadResult = manager.downloadArtifacts(mavenProjects, artifacts, true, false)

          withContext(Dispatchers.EDT) {
            if (!downloadResult.unresolvedSources.isEmpty()) {
              val builder = HtmlBuilder()
              builder.append(MavenProjectBundle.message("sources.not.found.for"))
              for ((count, each) in downloadResult.unresolvedSources.withIndex()) {
                if (count > 5) {
                  builder.append(HtmlChunk.br()).append(MavenProjectBundle.message("and.more"))
                  break
                }
                builder.append(HtmlChunk.br()).append(each.displayString)
              }
              cleanUpUnresolvedSourceFiles(project, downloadResult.unresolvedSources)
              Notifications.Bus.notify(Notification(MavenUtil.MAVEN_NOTIFICATION_GROUP,
                                                    MavenProjectBundle.message("maven.sources.cannot.download"),
                                                    builder.wrapWithHtmlBody().toString(),
                                                    NotificationType.WARNING),
                                       project)
            }

            if (downloadResult.resolvedSources.isEmpty()) {
              resultWrapper.setRejected()
            }
            else {
              resultWrapper.setDone()
            }
          }
        }

        return resultWrapper
      }
    })
  }

  private fun cleanUpUnresolvedSourceFiles(project: Project, mavenIds: Collection<MavenId>) {
    for (mavenId in mavenIds) {
      val parentFile = MavenUtil.getRepositoryParentFile(project, mavenId) ?: continue
      try {
        Files.list(parentFile).use { paths ->
          paths.filter { isTargetFile(it.fileName.toString(), MavenExtraArtifactType.SOURCES) }
            .forEach {
              try {
                FileUtil.delete(it)
              }
              catch (e: IOException) {
                MavenLog.LOG.warn("$it not deleted", e)
              }
            }
        }
      }
      catch (e: IOException) {
        MavenLog.LOG.warn("$parentFile cannot be listed", e)
      }
    }
  }

  private fun isTargetFile(name: String, type: MavenExtraArtifactType): Boolean {
    return name.contains("-" + type.defaultClassifier) && name.contains("." + type.defaultExtension)
  }

  private fun findArtifacts(mavenProjects: Collection<MavenProject>,
                            orderEntries: List<LibraryOrderEntry>): Collection<MavenArtifact> {
    val artifacts: MutableCollection<MavenArtifact> = HashSet()
    for (each in mavenProjects) {
      for (entry in orderEntries) {
        val artifact = MavenRootModelAdapter.findArtifact(each, entry.getLibrary())
        if (artifact != null && "system" != artifact.scope) {
          artifacts.add(artifact)
        }
      }
    }
    return artifacts
  }

  private fun getMavenProjects(psiFile: PsiFile): Collection<MavenProject> {
    val project = psiFile.getProject()
    val result: MutableCollection<MavenProject> = ArrayList()
    for (each in ProjectRootManager.getInstance(project).getFileIndex().getOrderEntriesForFile(psiFile.getVirtualFile())) {
      val mavenProject = MavenProjectsManager.getInstance(project).findProject(each.getOwnerModule())
      if (mavenProject != null) result.add(mavenProject)
    }
    return result
  }
}
