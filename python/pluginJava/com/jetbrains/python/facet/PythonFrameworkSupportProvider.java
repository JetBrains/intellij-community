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

import com.intellij.facet.FacetManager;
import com.intellij.ide.util.frameworkSupport.FrameworkSupportConfigurable;
import com.intellij.ide.util.frameworkSupport.FrameworkSupportModel;
import com.intellij.ide.util.frameworkSupport.FrameworkSupportProvider;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.jetbrains.python.module.PythonModuleType;
import icons.PythonIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author yole
 */
public class PythonFrameworkSupportProvider extends FrameworkSupportProvider {
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

  public boolean isEnabledForModuleType(@NotNull ModuleType moduleType) {
    return !(moduleType instanceof PythonModuleType);
  }

  @Override
  public boolean isSupportAlreadyAdded(@NotNull Module module) {
    return FacetManager.getInstance(module).getFacetsByType(PythonFacetType.getInstance().getId()).size() > 0;
  }
}
