// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl.configuration;

import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurableProvider;
import com.intellij.openapi.project.Project;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.Contract;

public final class PythonContentEntriesConfigurableProvider extends ConfigurableProvider {
  private final Project myProject;

  public PythonContentEntriesConfigurableProvider(Project project) {
    myProject = project;
  }

  @Override
  public boolean canCreateConfigurable() {
    return ArrayUtil.getFirstElement(ModuleManager.getInstance(myProject).getModules()) != null;
  }

  @Contract(" -> !null")
  @Override
  public Configurable createConfigurable() {
    return new PythonContentEntriesConfigurable(myProject);
  }
}
