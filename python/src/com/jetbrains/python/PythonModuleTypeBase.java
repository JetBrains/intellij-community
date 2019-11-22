// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python;

import com.intellij.facet.FacetType;
import com.intellij.ide.util.projectWizard.ModuleBuilder;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.ModuleTypeManager;
import icons.PythonIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.java.JavaSourceRootType;
import org.jetbrains.jps.model.module.JpsModuleSourceRootType;

import javax.swing.*;

/**
 * @author yole
 */
public abstract class PythonModuleTypeBase<T extends ModuleBuilder> extends ModuleType<T> {
  public static ModuleType getInstance() {
    return ModuleTypeManager.getInstance().findByID(PyNames.PYTHON_MODULE_ID);
  }

  protected PythonModuleTypeBase() {
    super(PyNames.PYTHON_MODULE_ID);
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
    for (FacetType type : FacetType.EP_NAME.getExtensionList()) {
      if (type.getId().toString().equalsIgnoreCase("django")) {
        return basicDescription + " Supported technologies include <b>Django, Google App Engine, Mako, Jinja2</b> and others.";
      }
    }
    return basicDescription;
  }

  @NotNull
  @Override
  public Icon getNodeIcon(final boolean isOpened) {
    return PythonIcons.Python.PythonClosed;
  }

  @Override
  public boolean isMarkInnerSupportedFor(JpsModuleSourceRootType type) {
    return type == JavaSourceRootType.SOURCE;
  }
}
