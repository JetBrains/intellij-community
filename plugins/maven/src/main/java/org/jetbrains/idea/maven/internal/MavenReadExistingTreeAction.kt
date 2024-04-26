// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.internal

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.DumbAwareAction
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
       mavenProjectManager.projectsTree.updateAll(true, mavenProjectManager.generalSettings, indicator)
      }
    }



  }

}