package com.intellij.psi.search

import com.intellij.core.CoreProjectScopeBuilder
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.FileIndexFacade

class MockPsiProjectScopeBuilder(private val myProject: Project, fileIndexFacade: FileIndexFacade) :
  CoreProjectScopeBuilder(myProject, fileIndexFacade) {

  override fun buildAllScope(): GlobalSearchScope {
    return EverythingGlobalScope(myProject)
  }
}