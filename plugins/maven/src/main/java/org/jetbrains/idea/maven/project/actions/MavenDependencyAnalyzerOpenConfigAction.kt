// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.externalSystem.dependency.analyzer.*
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.idea.maven.utils.MavenUtil

class MavenDependencyAnalyzerOpenConfigAction : DependencyAnalyzerOpenConfigAction(MavenUtil.SYSTEM_ID) {

  override fun getConfigFile(e: AnActionEvent): VirtualFile? {
    val project = e.project ?: return null
    val dependency = e.getData(DependencyAnalyzerView.DEPENDENCY) ?: return null
    return getArtifactFile(project, dependency.data)
  }
}