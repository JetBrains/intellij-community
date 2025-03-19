// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.externalSystem.service.project.trusted.ExternalSystemTrustedProjectDialog
import com.intellij.openapi.fileEditor.FileDocumentManager
import org.jetbrains.idea.maven.buildtool.MavenSyncSpec
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.utils.MavenLog
import org.jetbrains.idea.maven.utils.MavenUtil
import org.jetbrains.idea.maven.utils.actions.MavenActionUtil

class IncrementalSyncAction : MavenProjectsManagerAction() {
  override fun isVisible(e: AnActionEvent): Boolean {
    return true
  }

  override fun isAvailable(e: AnActionEvent): Boolean {
    val dataContext = e.dataContext
    return MavenActionUtil.hasProject(dataContext) && MavenActionUtil.isMavenizedProject(dataContext)
  }

  @Suppress("deprecation")
  override fun perform(manager: MavenProjectsManager) {
    ExternalSystemTrustedProjectDialog.confirmLoadingUntrustedProject(manager.project, MavenUtil.SYSTEM_ID)
    FileDocumentManager.getInstance().saveAllDocuments()
    MavenLog.LOG.info("IncrementalSyncAction scheduleUpdateAllMavenProjects")
    manager.scheduleUpdateAllMavenProjects(MavenSyncSpec.incremental("IncrementalSyncAction", true))
  }
}