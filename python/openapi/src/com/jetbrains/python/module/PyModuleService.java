// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.module;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public abstract class PyModuleService {
  @Nullable
  public abstract Sdk findPythonSdk(@NotNull Module module);

  public void forAllFacets(@NotNull Module module, @NotNull Consumer<Object> facetConsumer) {
  }

  public static PyModuleService getInstance() {
    return ApplicationManager.getApplication().getService(PyModuleService.class);
  }


  public boolean isFileIgnored(@NotNull VirtualFile file) {
    return false;
  }
}
