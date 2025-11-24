// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.module;

import com.intellij.facet.Facet;
import com.intellij.facet.FacetConfiguration;
import com.intellij.facet.FacetManager;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PythonModuleTypeBase;
import com.jetbrains.python.facet.PythonFacetSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class PyModuleServiceImpl extends PyModuleService {

  @Override
  public boolean isFileIgnored(@NotNull VirtualFile file) {
    return FileTypeManager.getInstance().isFileIgnored(file);
  }

  @Override
  public @Nullable Sdk findPythonSdk(@NotNull Module module) {
    var sdk = super.findPythonSdk(module);
    if (sdk != null) {
      return sdk;
    }
    for (Facet<?> facet : FacetManager.getInstance(module).getAllFacets()) {
      final FacetConfiguration configuration = facet.getConfiguration();
      if (configuration instanceof PythonFacetSettings) {
        return ((PythonFacetSettings)configuration).getSdk();
      }
    }
    return ModuleRootManager.getInstance(module).getSdk();
  }

  @Override
  public void forAllFacets(@NotNull Module module, @NotNull Consumer<Object> facetConsumer) {
    for (Facet<?> f : FacetManager.getInstance(module).getAllFacets()) {
      facetConsumer.consume(f);
    }
  }

  @Override
  public boolean isPythonModule(@NotNull Module module) {
    ModuleType<?> type = ModuleType.get(module);
    if (type instanceof PythonModuleTypeBase || type.getId().equals(PyNames.PYTHON_MODULE_ID)) {
      return true;
    }
    final Facet<?>[] allFacets = FacetManager.getInstance(module).getAllFacets();
    for (Facet<?> facet : allFacets) {
      if (facet.getConfiguration() instanceof PythonFacetSettings) {
        return true;
      }
    }
    return false;
  }
}
