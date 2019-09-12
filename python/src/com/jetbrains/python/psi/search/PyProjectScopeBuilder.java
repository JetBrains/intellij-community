// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.search;

import com.intellij.injected.editor.VirtualFileWindow;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.FileIndexFacade;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.*;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.codeInsight.typing.PyTypeShed;
import com.jetbrains.python.sdk.PythonSdkUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 *
 * @author yole
 */
public class PyProjectScopeBuilder extends ProjectScopeBuilderImpl {
  public PyProjectScopeBuilder(Project project) {
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
      public boolean isSearchOutsideRootModel() {
        return true;
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
        if (file instanceof VirtualFileWindow) return true;
        return fileIndex.isInContent(file);
      }
    };
  }
}
