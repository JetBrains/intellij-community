// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.facet;

import com.intellij.facet.FacetManager;
import com.intellij.ide.util.frameworkSupport.FrameworkSupportConfigurable;
import com.intellij.ide.util.frameworkSupport.FrameworkSupportModel;
import com.intellij.ide.util.frameworkSupport.FrameworkSupportProvider;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.jetbrains.python.module.PythonModuleType;
import com.jetbrains.python.icons.PythonIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;


public final class PythonFrameworkSupportProvider extends FrameworkSupportProvider {
  public PythonFrameworkSupportProvider() {
    super("Python", PythonFacetType.getInstance().getPresentableName());
  }

  @Override
  public Icon getIcon() {
    return PythonIcons.Python.Python;
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
    return FacetManager.getInstance(module).getFacetsByType(PythonFacetType.getInstance().getId()).size() > 0;
  }
}
