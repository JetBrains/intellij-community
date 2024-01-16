// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.search;

import com.intellij.injected.editor.VirtualFileWindow;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.FileIndexFacade;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.*;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;


final class PyProjectScopeBuilder extends ProjectScopeBuilderImpl {
  PyProjectScopeBuilder(Project project) {
    super(project);
  }

  /**
   * This method is necessary because of the check in IndexCacheManagerImpl.shouldBeFound()
   * In Python, files in PYTHONPATH are library classes but not library sources, so the check in that method ensures that
   * nothing is found there even when the user selects the "Project and Libraries" scope. Thus, we have to override the
   * isSearchOutsideRootModel() flag for that scope.
   *
   * @return all scope
   */
  @NotNull
  @Override
  public GlobalSearchScope buildAllScope() {
    return new ProjectAndLibrariesScope(myProject) {
      @Override
      public boolean contains(@NotNull VirtualFile file) {
        if (file instanceof ProjectAwareVirtualFile) {
          return ((ProjectAwareVirtualFile)file).isInProject(Objects.requireNonNull(getProject()));
        }
        return super.contains(file);
      }
    };
  }

  /**
   * Project directories are commonly included in PYTHONPATH and as a result are listed as library classes. Core logic
   * includes them in project scope only if they are also marked as source roots. Python code is often not marked as source
   * root, so we need to override the core logic and check only whether the file is under project content.
   *
   * @return project search scope
   */
  @NotNull
  @Override
  public GlobalSearchScope buildProjectScope() {
    final FileIndexFacade fileIndex = FileIndexFacade.getInstance(myProject);
    return new ProjectScopeImpl(myProject, fileIndex) {
      @Override
      public boolean contains(@NotNull VirtualFile file) {
        if (file instanceof ProjectAwareVirtualFile) {
          return ((ProjectAwareVirtualFile)file).isInProject(Objects.requireNonNull(getProject()));
        }
        if (file instanceof VirtualFileWindow) return true;
        return fileIndex.isInContent(file);
      }
    };
  }
}
