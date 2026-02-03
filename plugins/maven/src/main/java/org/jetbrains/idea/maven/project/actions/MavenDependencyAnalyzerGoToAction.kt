// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.externalSystem.dependency.analyzer.DependencyAnalyzerGoToAction
import com.intellij.openapi.externalSystem.dependency.analyzer.DependencyAnalyzerView
import com.intellij.pom.Navigatable
import org.jetbrains.idea.maven.navigator.MavenNavigationUtil
import org.jetbrains.idea.maven.utils.MavenUtil

class MavenDependencyAnalyzerGoToAction : DependencyAnalyzerGoToAction(MavenUtil.SYSTEM_ID) {

  override fun getNavigatable(e: AnActionEvent): Navigatable? {
    val project = e.project ?: return null
    val dependency = e.getData(DependencyAnalyzerView.DEPENDENCY) ?: return null
    val parent = dependency.parent ?: return null
    val mavenId = getMavenId(dependency.data) ?: return null
    val groupId = mavenId.groupId ?: return null
    val artifactId = mavenId.artifactId ?: return null
    val artifactFile = getArtifactFile(project, parent.data) ?: return null
    return MavenNavigationUtil.createNavigatableForDependency(project, artifactFile, groupId, artifactId)
  }
}