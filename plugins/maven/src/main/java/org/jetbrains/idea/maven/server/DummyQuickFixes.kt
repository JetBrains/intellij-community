// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.server

import com.intellij.build.issue.BuildIssueQuickFix
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.utils.MavenUtil
import java.util.concurrent.CompletableFuture

class TrustProjectQuickFix : BuildIssueQuickFix {
  override val id = ID

  override fun runQuickFix(project: Project, dataContext: DataContext): CompletableFuture<*> {
    val future = CompletableFuture<Void>()
    ApplicationManager.getApplication().invokeLater {
      try {
        val result = MavenUtil.isProjectTrustedEnoughToImport(project)
        if (result) {
          MavenProjectsManager.getInstance(project).forceUpdateAllProjectsOrFindAllAvailablePomFiles()
        }
        future.complete(null)
      }
      catch (e: Throwable) {
        future.completeExceptionally(e)
      }

    }

    return future
  }

  companion object {
    val ID = "TRUST_MAVEN_PROJECT_QUICK_FIX_ID"
  }
}

class RestartMavenEmbeddersQuickFix : BuildIssueQuickFix {
  override val id = ID

  override fun runQuickFix(project: Project, dataContext: DataContext): CompletableFuture<*> {
    val future = CompletableFuture<Void>()
    ApplicationManager.getApplication().executeOnPooledThread {
      MavenProjectsManager.getInstance(project).embeddersManager.reset()
      MavenProjectsManager.getInstance(project).forceUpdateAllProjectsOrFindAllAvailablePomFiles()
      future.complete(null)
    }
    return future
  }

  companion object {
    const val ID = "RESTART_MAVEN_QUICK_FIX_ID"
  }

}