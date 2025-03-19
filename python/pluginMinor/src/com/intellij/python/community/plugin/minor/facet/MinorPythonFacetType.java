// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.plugin.minor.facet;

import com.intellij.facet.Facet;
import com.intellij.facet.FacetType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.icons.PythonPsiApiIcons;
import com.jetbrains.python.sdk.PythonSdkType;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

@ApiStatus.Internal
public final class MinorPythonFacetType extends FacetType<MinorPythonFacet, PythonFacetConfiguration> {
  private static final @NonNls String ID = "Python";

  public static MinorPythonFacetType getInstance() {
    return findInstance(MinorPythonFacetType.class);
  }

  MinorPythonFacetType() {
    super(MinorPythonFacet.ID, ID, PyBundle.message("python.facet.name"));
  }

  @Override
  public PythonFacetConfiguration createDefaultConfiguration() {
    PythonFacetConfiguration result = new PythonFacetConfiguration();
    List<Sdk> sdks = ProjectJdkTable.getInstance().getSdksOfType(PythonSdkType.getInstance());
    if (!sdks.isEmpty()) {
      result.setSdk(sdks.get(0));
    }
    return result;
  }

  @Override
  public MinorPythonFacet createFacet(@NotNull Module module,
                                      String name,
                                      @NotNull PythonFacetConfiguration configuration,
                                      @Nullable Facet underlyingFacet) {
    return new MinorPythonFacet(this, module, name, configuration, underlyingFacet);
  }

  @Override
  public boolean isSuitableModuleType(ModuleType moduleType) {
    return !(moduleType.getId().equals(PyNames.PYTHON_MODULE_ID));
  }

  @Override
  public Icon getIcon() {
    return  PythonPsiApiIcons.Python;
  }
}
