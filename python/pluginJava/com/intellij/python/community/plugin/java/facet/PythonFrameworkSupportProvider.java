// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.plugin.java.facet;

import com.intellij.facet.FacetManager;
import com.intellij.ide.util.frameworkSupport.FrameworkSupportConfigurable;
import com.intellij.ide.util.frameworkSupport.FrameworkSupportModel;
import com.intellij.ide.util.frameworkSupport.FrameworkSupportProvider;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.jetbrains.python.module.PythonModuleType;
import com.jetbrains.python.psi.icons.PythonPsiApiIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;


public final class PythonFrameworkSupportProvider extends FrameworkSupportProvider {
  public PythonFrameworkSupportProvider() {
    super("Python", JavaPythonFacetType.getInstance().getPresentableName());
  }

  @Override
  public Icon getIcon() {
    return  PythonPsiApiIcons.Python;
  }

  @NotNull
  @Override
  public FrameworkSupportConfigurable createConfigurable(@NotNull FrameworkSupportModel model) {
    return new PythonFrameworkSupportConfigurable(model);
  }

  @Override
  public boolean isEnabledForModuleType(@NotNull ModuleType moduleType) {
    return !(moduleType instanceof PythonModuleType);
  }

  @Override
  public boolean isSupportAlreadyAdded(@NotNull Module module) {
    return FacetManager.getInstance(module).getFacetsByType(JavaPythonFacetType.getInstance().getId()).size() > 0;
  }
}
