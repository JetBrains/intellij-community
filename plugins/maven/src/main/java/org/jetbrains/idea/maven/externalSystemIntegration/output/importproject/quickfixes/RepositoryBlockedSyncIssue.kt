// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.externalSystemIntegration.output.importproject.quickfixes

import com.intellij.build.issue.BuildIssue
import com.intellij.build.issue.BuildIssueQuickFix
import com.intellij.build.issue.quickfix.OpenFileQuickFix
import com.intellij.execution.filters.UrlFilter
import com.intellij.find.FindModel
import com.intellij.find.findInProject.FindInProjectManager
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project
import com.intellij.pom.Navigatable
import org.jetbrains.idea.maven.buildtool.quickfix.OpenMavenSettingsQuickFix
import org.jetbrains.idea.maven.execution.SyncBundle.message
import org.jetbrains.idea.maven.project.MavenProjectsManager
import java.util.concurrent.CompletableFuture

object RepositoryBlockedSyncIssue {

  @JvmStatic
  fun getIssue(project: Project, title: String): BuildIssue {
    val quickFixes = mutableListOf<BuildIssueQuickFix>()
    val issueDescription = StringBuilder(title)
    issueDescription.append("\n\n")
      .append(message("maven.sync.quickfixes.repository.blocked"))
      .append("\n")

    val openSettingsXmlQuickFix = MavenProjectsManager.getInstance(project).generalSettings.effectiveUserSettingsIoFile
      ?.let { if (it.exists()) it else null }
      ?.toPath()
      ?.let {
        val quickFix = OpenFileQuickFix(it, null)
        quickFixes.add(quickFix)
        issueDescription
          .append(message("maven.sync.quickfixes.repository.blocked.show.settings", quickFix.id))
          .append("\n")
        quickFix
      }

    val repoUrls = UrlFilter().applyFilter(title, title.length)
      ?.resultItems!!
      .asSequence()
      .filter { it != null }
      .map { title.substring(it.highlightStartOffset, it.highlightEndOffset) }
      .filter { !it.contains("0.0.0.0", false) }
      .toList()

    repoUrls.forEach {
        val quickFix = FindBlockedRepositoryQuickFix(it)
        quickFixes.add(quickFix)
        issueDescription
          .append(message("maven.sync.quickfixes.repository.blocked.find.repository", quickFix.id, it))
          .append("\n")
    }

    issueDescription
      .append(message("maven.sync.quickfixes.repository.blocked.add.mirror", repoUrls.joinToString(),
                      openSettingsXmlQuickFix?.id ?: ""))
      .append("\n")

    val quickFix = OpenMavenSettingsQuickFix()
    quickFixes.add(quickFix)
    issueDescription
      .append(message("maven.sync.quickfixes.repository.blocked.downgrade", quickFix.id))
      .append("\n")

    return object : BuildIssue {
      override val title: String = title
      override val description: String = issueDescription.toString()
      override val quickFixes = quickFixes
      override fun getNavigatable(project: Project): Navigatable? = null
    }
  }
}

class FindBlockedRepositoryQuickFix(val repoUrl: String) : BuildIssueQuickFix {

  override val id: String get() = "maven_find_blocked_repository_quick_fix_$repoUrl"

  override fun runQuickFix(project: Project, dataContext: DataContext): CompletableFuture<*> {
    val findModel = FindModel()
    findModel.stringToFind = repoUrl
    findModel.isProjectScope = true
    FindInProjectManager.getInstance(project).findInProject(dataContext, findModel)

    return CompletableFuture.completedFuture(null)
  }
}

