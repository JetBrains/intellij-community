// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project

import com.intellij.openapi.Disposable
import com.intellij.openapi.externalSystem.dependency.analyzer.DummyDependencyAnalyzerContributor
import com.intellij.openapi.project.Project

class MavenDependencyAnalyzerContributor(project: Project): DummyDependencyAnalyzerContributor(project) {
  override fun whenDataChanged(listener: () -> Unit, parentDisposable: Disposable) {

  }
}