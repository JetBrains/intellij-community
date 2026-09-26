// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.internal

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.util.NlsContexts
import com.intellij.platform.util.progress.RawProgressReporter
import org.jetbrains.idea.maven.project.MavenEmbedderWrappersManager
import org.jetbrains.idea.maven.project.MavenProjectBundle
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.utils.MavenUtil

class MavenReadExistingTreeAction : DumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project
    if (project == null) return
    val mavenProjectManager = MavenProjectsManager.getInstance(project)

    MavenUtil.run(MavenProjectBundle.message("action.Maven.ReReadAll.text")) { indicator ->
      runBlockingCancellable {
        val mavenEmbedderWrappers = project.service<MavenEmbedderWrappersManager>().createMavenEmbedderWrappers()
        mavenEmbedderWrappers.use {
          val files = mavenProjectManager.state.originalFiles
          mavenProjectManager.projectsTree.updateAllFiles(files, true, mavenProjectManager.generalSettings, mavenProjectManager.explicitProfiles, mavenEmbedderWrappers, toRawProgressReporter(indicator.indicator))
        }
      }
    }
  }

  private fun toRawProgressReporter(progressIndicator: ProgressIndicator): RawProgressReporter {
    return object : RawProgressReporter {
      override fun text(text: @NlsContexts.ProgressText String?) {
        progressIndicator.text = text
      }
    }
  }

}