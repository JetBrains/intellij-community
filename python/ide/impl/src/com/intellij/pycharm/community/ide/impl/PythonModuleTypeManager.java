// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl;

import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.impl.ModuleTypeManagerImpl;
import org.jetbrains.annotations.NotNull;

final class PythonModuleTypeManager extends ModuleTypeManagerImpl {
  @Override
  public @NotNull ModuleType<?> getDefaultModuleType() {
    return new PlatformPythonModuleType();
  }
}
