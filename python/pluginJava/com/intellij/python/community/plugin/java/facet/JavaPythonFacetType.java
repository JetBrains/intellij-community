// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.plugin.java.facet;

import com.intellij.facet.Facet;
import com.intellij.facet.FacetType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.module.PythonModuleType;
import com.jetbrains.python.psi.icons.PythonPsiApiIcons;
import com.jetbrains.python.sdk.PythonSdkType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;


public final class JavaPythonFacetType extends FacetType<JavaPythonFacet, JavaPythonFacetConfiguration> {

  @NonNls
  private static final String ID = "Python";

  public static JavaPythonFacetType getInstance() {
    return findInstance(JavaPythonFacetType.class);
  }

  public JavaPythonFacetType() {
    super(JavaPythonFacet.ID, ID, PyBundle.message("python.facet.name"));
  }

  @Override
  public JavaPythonFacetConfiguration createDefaultConfiguration() {
    JavaPythonFacetConfiguration result = new JavaPythonFacetConfiguration();
    List<Sdk> sdks = ProjectJdkTable.getInstance().getSdksOfType(PythonSdkType.getInstance());
    if (sdks.size() > 0) {
      result.setSdk(sdks.get(0));
    }
    return result;
  }

  @Override
  public JavaPythonFacet createFacet(@NotNull Module module, String name, @NotNull JavaPythonFacetConfiguration configuration, @Nullable Facet underlyingFacet) {
    return new JavaPythonFacet(this, module, name, configuration, underlyingFacet);
  }

  @Override
  public boolean isSuitableModuleType(ModuleType moduleType) {
    return !(moduleType instanceof PythonModuleType);
  }

  @Override
  public Icon getIcon() {
    return  PythonPsiApiIcons.Python;
  }
}
