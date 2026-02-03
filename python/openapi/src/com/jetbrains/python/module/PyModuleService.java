// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.module;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public abstract class PyModuleService {
  public @Nullable Sdk findPythonSdk(@NotNull Module module) {
    return ModuleRootManager.getInstance(module).getSdk();
  }

  public void forAllFacets(@NotNull Module module, @NotNull Consumer<Object> facetConsumer) {
  }

  public static PyModuleService getInstance() {
    return ApplicationManager.getApplication().getService(PyModuleService.class);
  }


  public boolean isFileIgnored(@NotNull VirtualFile file) {
    return false;
  }
  @ApiStatus.Internal
  public abstract boolean isPythonModule(@NotNull Module module);
}
