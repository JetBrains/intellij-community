// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.project

import com.intellij.ide.impl.isTrusted
import com.intellij.openapi.externalSystem.service.project.UntrustedProjectModeProvider
import com.intellij.openapi.project.Project
import org.jetbrains.idea.maven.utils.MavenUtil

class MavenUntrustedModeProvider : UntrustedProjectModeProvider {

  override val systemId = MavenUtil.SYSTEM_ID

  override fun shouldShowEditorNotification(project: Project): Boolean {
    return MavenProjectsManager.getInstance(project).isMavenizedProject
  }

  override fun loadAllLinkedProjects(project: Project) {
    val manager = MavenProjectsManager.getInstance(project)
    if (manager.isMavenizedProject) {
      manager.forceUpdateAllProjectsOrFindAllAvailablePomFiles()
    }
  }
}