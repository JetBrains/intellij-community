// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python;

import com.intellij.facet.FacetType;
import com.intellij.ide.util.projectWizard.ModuleBuilder;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.ModuleTypeManager;
import icons.PythonIcons;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.java.JavaSourceRootType;
import org.jetbrains.jps.model.module.JpsModuleSourceRootType;

import javax.swing.*;

/**
 * @author yole
 */
public abstract class PythonModuleTypeBase<T extends ModuleBuilder> extends ModuleType<T> {
  public static ModuleType getInstance() {
    return ModuleTypeManager.getInstance().findByID(PYTHON_MODULE);
  }

  @NonNls public static final String PYTHON_MODULE = "PYTHON_MODULE";

  protected PythonModuleTypeBase() {
    super(PYTHON_MODULE);
  }

  @Override
  @NotNull
  public String getName() {
    return "Python Module";
  }

  @Override
  @NotNull
  public String getDescription() {
    String basicDescription = "Python modules are used for developing <b>Python</b> applications.";
    FacetType[] facetTypes = Extensions.getExtensions(FacetType.EP_NAME);
    for (FacetType type : facetTypes) {
      if (type.getId().toString().equalsIgnoreCase("django")) {
        return basicDescription + " Supported technologies include <b>Django, Google App Engine, Mako, Jinja2</b> and others.";
      }
    }
    return basicDescription;
  }

  @Override
  public Icon getNodeIcon(final boolean isOpened) {
    return PythonIcons.Python.PythonClosed;
  }

  @Override
  public boolean isMarkInnerSupportedFor(JpsModuleSourceRootType type) {
    return type == JavaSourceRootType.SOURCE;
  }
}
