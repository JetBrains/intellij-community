package com.jetbrains.python.psi.search;

import com.intellij.openapi.project.Project;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.ProjectAndLibrariesScope;
import com.intellij.psi.search.ProjectScopeBuilder;

/**
 * This class is necessary because of the check in IndexCacheManagerImpl.shouldBeFound()
 * In Python, files in PYTHONPATH are library classes but not library sources, so the check in that method ensures that
 * nothing is found there even when the user selects the "Project and Libraries" scope. Thus, we have to override the
 * isSearchOutsideRootModel() flag for that scope.
 * 
 * @author yole
 */
public class PyProjectScopeBuilder extends ProjectScopeBuilder {
  private final Project myProject;
  
  public PyProjectScopeBuilder(Project project) {
    super(project);
    myProject = project;
  }

  @Override
  public GlobalSearchScope buildAllScope() {
    return new ProjectAndLibrariesScope(myProject) {
      @Override
      public boolean isSearchOutsideRootModel() {
        return true;
      }
    };
  }
}
