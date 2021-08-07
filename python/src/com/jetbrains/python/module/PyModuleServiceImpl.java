// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.module;

import com.intellij.facet.Facet;
import com.intellij.facet.FacetConfiguration;
import com.intellij.facet.FacetManager;
import com.intellij.ide.util.projectWizard.ModuleBuilder;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.DirectoryProjectGenerator;
import com.intellij.util.Consumer;
import com.jetbrains.python.facet.PythonFacetSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class PyModuleServiceImpl extends PyModuleServiceEx {
  @Override
  public ModuleBuilder createPythonModuleBuilder(DirectoryProjectGenerator generator) {
    return new PythonModuleBuilderBase(generator);
  }

  @Override
  public boolean isFileIgnored(@NotNull VirtualFile file) {
    return FileTypeManager.getInstance().isFileIgnored(file);
  }

  @Nullable
  @Override
  public Sdk findPythonSdk(@NotNull Module module) {
    for (Facet<?> facet : FacetManager.getInstance(module).getAllFacets()) {
      final FacetConfiguration configuration = facet.getConfiguration();
      if (configuration instanceof PythonFacetSettings) {
        return ((PythonFacetSettings)configuration).getSdk();
      }
    }
    return null;
  }

  @Override
  public void forAllFacets(@NotNull Module module, @NotNull Consumer<Object> facetConsumer) {
    for (Facet<?> f : FacetManager.getInstance(module).getAllFacets()) {
      facetConsumer.consume(f);
    }
  }
}
