// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.project.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.externalSystem.service.project.trusted.ExternalSystemTrustedProjectDialog.confirmLoadingUntrustedProject
import com.intellij.openapi.fileEditor.FileDocumentManager
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.utils.MavenLog
import org.jetbrains.idea.maven.utils.MavenUtil
import org.jetbrains.idea.maven.utils.actions.MavenActionUtil

class ReimportAction : MavenProjectsManagerAction() {
  override fun isVisible(e: AnActionEvent): Boolean {
    return true
  }

  override fun isAvailable(e: AnActionEvent): Boolean {
    return MavenActionUtil.hasProject(e.dataContext)
  }

  @Suppress("deprecation")
  override fun perform(manager: MavenProjectsManager) {
    confirmLoadingUntrustedProject(manager.project, MavenUtil.SYSTEM_ID)
    FileDocumentManager.getInstance().saveAllDocuments()
    MavenLog.LOG.info("ReimportAction forceUpdateAllProjectsOrFindAllAvailablePomFiles")
    manager.forceUpdateAllProjectsOrFindAllAvailablePomFiles()
  }
}
