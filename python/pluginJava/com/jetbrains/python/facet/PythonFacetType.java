// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

  @Override
  public PythonFacetConfiguration createDefaultConfiguration() {
    PythonFacetConfiguration result = new PythonFacetConfiguration();
    List<Sdk> sdks = ProjectJdkTable.getInstance().getSdksOfType(PythonSdkType.getInstance());
    if (sdks.size() > 0) {
      result.setSdk(sdks.get(0));
    }
    return result;
  }

  @Override
  public PythonFacet createFacet(@NotNull Module module, String name, @NotNull PythonFacetConfiguration configuration, @Nullable Facet underlyingFacet) {
    return new PythonFacet(this, module, name, configuration, underlyingFacet);
  }

  @Override
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

    @NotNull
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
