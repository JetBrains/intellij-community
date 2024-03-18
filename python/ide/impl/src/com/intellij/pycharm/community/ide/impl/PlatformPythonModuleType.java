// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl;

import com.intellij.ide.util.projectWizard.EmptyModuleBuilder;
import com.intellij.openapi.module.ModuleType;
import com.jetbrains.python.PythonModuleTypeBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.java.JavaSourceRootType;
import org.jetbrains.jps.model.module.JpsModuleSourceRootType;


public class PlatformPythonModuleType extends PythonModuleTypeBase<EmptyModuleBuilder> {
  @Override
  public @NotNull EmptyModuleBuilder createModuleBuilder() {
    return new EmptyModuleBuilder() {
      @Override
      public ModuleType getModuleType() {
        return getInstance();
      }
    };
  }

  @Override
  public boolean isSupportedRootType(JpsModuleSourceRootType<?> type) {
    return type == JavaSourceRootType.SOURCE || type == JavaSourceRootType.TEST_SOURCE;
  }
}
