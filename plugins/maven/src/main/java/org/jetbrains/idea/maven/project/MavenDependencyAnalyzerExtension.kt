// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.project

import com.intellij.openapi.Disposable
import com.intellij.openapi.externalSystem.dependency.analyzer.DependencyAnalyzerContributor
import com.intellij.openapi.externalSystem.dependency.analyzer.DependencyAnalyzerExtension
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.project.Project
import org.jetbrains.idea.maven.utils.MavenUtil

class MavenDependencyAnalyzerExtension : DependencyAnalyzerExtension {

  override fun isApplicable(systemId: ProjectSystemId): Boolean {
    return systemId == MavenUtil.SYSTEM_ID
  }

  override fun createContributor(project: Project, parentDisposable: Disposable): DependencyAnalyzerContributor {
    return MavenDependencyAnalyzerContributor(project)
  }
}