/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

/**
 * @author yole
 */
public class PyModuleServiceImpl extends PyModuleServiceEx {
  @Override
  public ModuleBuilder createPythonModuleBuilder(DirectoryProjectGenerator generator) {
    return new PythonModuleBuilderBase(generator);
  }

  @Override
  public boolean isFileIgnored(@NotNull VirtualFile file) {
    final FileTypeManager fileTypeManager = FileTypeManager.getInstance();
    return fileTypeManager.isFileIgnored(file);
  }

  @Nullable
  @Override
  public Sdk findPythonSdk(@NotNull Module module) {
    final Facet[] facets = FacetManager.getInstance(module).getAllFacets();
    for (Facet facet : facets) {
      final FacetConfiguration configuration = facet.getConfiguration();
      if (configuration instanceof PythonFacetSettings) {
        return ((PythonFacetSettings)configuration).getSdk();
      }
    }
    return null;
  }

  @Override
  public void forAllFacets(@NotNull Module module, @NotNull Consumer<Object> facetConsumer) {
    for(Facet f: FacetManager.getInstance(module).getAllFacets()) {
      facetConsumer.consume(f);
    }
  }
}
