// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.project.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemProjectId
import com.intellij.openapi.externalSystem.dependency.analyzer.AbstractAnalyzeDependenciesAction
import com.intellij.openapi.externalSystem.dependency.analyzer.DependenciesContributor
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import org.jetbrains.idea.maven.utils.MavenUtil

class AnalyzeDependenciesAction : AbstractAnalyzeDependenciesAction() {
  override fun getSystemId(e: AnActionEvent): ProjectSystemId = MavenUtil.SYSTEM_ID

  override fun getProjectId(e: AnActionEvent): ExternalSystemProjectId? = null

  override fun getDependency(e: AnActionEvent): DependenciesContributor.Dependency? = null
}