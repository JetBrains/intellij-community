// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.externalSystemIntegration.output.importproject.quickfixes

import com.intellij.build.issue.BuildIssue
import com.intellij.build.issue.BuildIssueQuickFix
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project
import com.intellij.pom.Navigatable
import org.jetbrains.idea.maven.project.MavenProjectBundle
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.server.MavenServerManager
import java.util.concurrent.CompletableFuture

object DownloadArtifactBuildIssue {
  fun getIssue(title: String, errorMessage: String): BuildIssue {
    val quickFixes = listOf(ForceUpdateSnapshotsImportQuickFix())

    val issueDescription = StringBuilder(errorMessage)
      .append("\n\n")
      .append(MavenProjectBundle.message("maven.quickfix.cannot.artifact.download", ForceUpdateSnapshotsImportQuickFix.ID))
      .toString()

    return object : BuildIssue {
      override val title: String = title
      override val description: String = issueDescription
      override val quickFixes = quickFixes
      override fun getNavigatable(project: Project): Navigatable? = null
    }
  }
}

class ForceUpdateSnapshotsImportQuickFix() : BuildIssueQuickFix {

  override val id: String = ID

  override fun runQuickFix(project: Project, dataContext: DataContext): CompletableFuture<*> {
    MavenServerManager.getInstance().shutdown(false)
    MavenProjectsManager.getInstance(project).setForceUpdateSnapshots(true)
    MavenProjectsManager.getInstance(project).forceUpdateProjects()
    return CompletableFuture.completedFuture(null)
  }

  companion object {
    const val ID = "force_update_snapshots_import_quick_fix"
  }
}

