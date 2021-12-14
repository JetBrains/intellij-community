// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.project.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.externalSystem.dependency.analyzer.AbstractDependencyAnalyzerAction
import com.intellij.openapi.externalSystem.dependency.analyzer.DependencyContributor
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import org.jetbrains.idea.maven.utils.MavenUtil

class MavenDependencyAnalyzerAction : AbstractDependencyAnalyzerAction() {
  override fun getSystemId(e: AnActionEvent): ProjectSystemId = MavenUtil.SYSTEM_ID

  override fun getExternalProjectPath(e: AnActionEvent): String? = null

  override fun getDependency(e: AnActionEvent): DependencyContributor.Dependency? = null
}