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
package com.jetbrains.python.facet;

import com.intellij.facet.Facet;
import com.intellij.facet.FacetType;
import com.intellij.framework.detection.FacetBasedFrameworkDetector;
import com.intellij.framework.detection.FileContentPattern;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.patterns.ElementPattern;
import com.intellij.util.indexing.FileContent;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.module.PythonModuleType;
import com.jetbrains.python.sdk.PythonSdkType;
import icons.PythonIcons;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

/**
 * @author yole
 */
public class PythonFacetType extends FacetType<PythonFacet, PythonFacetConfiguration> {

  @NonNls
  private static final String ID = "Python";

  public static PythonFacetType getInstance() {
    return findInstance(PythonFacetType.class);
  }

  public PythonFacetType() {
    super(PythonFacet.ID, ID, "Python");
  }

  public PythonFacetConfiguration createDefaultConfiguration() {
    PythonFacetConfiguration result = new PythonFacetConfiguration();
    List<Sdk> sdks = ProjectJdkTable.getInstance().getSdksOfType(PythonSdkType.getInstance());
    if (sdks.size() > 0) {
      result.setSdk(sdks.get(0));
    }
    return result;
  }

  public PythonFacet createFacet(@NotNull Module module, String name, @NotNull PythonFacetConfiguration configuration, @Nullable Facet underlyingFacet) {
    return new PythonFacet(this, module, name, configuration, underlyingFacet);
  }

  public boolean isSuitableModuleType(ModuleType moduleType) {
    return !(moduleType instanceof PythonModuleType);
  }

  @Override
  public Icon getIcon() {
    return PythonIcons.Python.Python;
  }

  public static class PythonFrameworkDetector extends FacetBasedFrameworkDetector<PythonFacet, PythonFacetConfiguration> {
    public PythonFrameworkDetector() {
      super("python");
    }

    @Override
    public FacetType<PythonFacet, PythonFacetConfiguration> getFacetType() {
      return PythonFacetType.getInstance();
    }

    @NotNull
    @Override
    public FileType getFileType() {
      return PythonFileType.INSTANCE;
    }

    @NotNull
    @Override
    public ElementPattern<FileContent> createSuitableFilePattern() {
      return FileContentPattern.fileContent();
    }
  }
}
