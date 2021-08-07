// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.externalSystemIntegration.output.importproject.quickfixes

import com.intellij.build.issue.BuildIssue
import com.intellij.build.issue.BuildIssueQuickFix
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.pom.Navigatable
import org.jetbrains.idea.maven.project.MavenProjectBundle
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.utils.MavenLog
import java.io.File
import java.util.concurrent.CompletableFuture

object DownloadArtifactBuildIssue {
  fun getIssue(title: String, quickFix: BuildIssueQuickFix): BuildIssue {
    val quickFixes = listOf(quickFix)

    return object : BuildIssue {
      override val title: String = title
      override val description: String = MavenProjectBundle.message("maven.quickfix.cannot.artifact.download", title, quickFix.id)
      override val quickFixes = quickFixes
      override fun getNavigatable(project: Project): Navigatable? = null
    }
  }
}

class CleanBrokenArtifactsAndReimportQuickFix(val unresolvedArtifactFiles: Collection<File>) : BuildIssueQuickFix {

  override val id: String = ID

  override fun runQuickFix(project: Project, dataContext: DataContext): CompletableFuture<*> {
    unresolvedArtifactFiles.forEach { deleteLastUpdatedFiles(it) }
    MavenProjectsManager.getInstance(project).forceUpdateProjects()
    return CompletableFuture.completedFuture(null)
  }

  private fun deleteLastUpdatedFiles(unresolvedArtifactDirectory: File) {
    MavenLog.LOG.info("start deleting lastUpdated file from $unresolvedArtifactDirectory")
    val files: Array<File> = unresolvedArtifactDirectory.listFiles { dir, name -> name.endsWith(".lastUpdated", true) } ?: return

    for (childFiles in files) {
      val deleted = FileUtil.delete(childFiles)
      if (!deleted) {
        MavenLog.LOG.warn("$childFiles not deleted")
      }
    }
  }

  companion object {
    const val ID = "clean_broken_artifacts_and_reimport_quick_fix"
  }
}

