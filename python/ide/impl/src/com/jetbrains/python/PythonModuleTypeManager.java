// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python;

import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.impl.ModuleTypeManagerImpl;
import org.jetbrains.annotations.NotNull;

final class PythonModuleTypeManager extends ModuleTypeManagerImpl {
  @Override
  public @NotNull ModuleType<?> getDefaultModuleType() {
    return new PlatformPythonModuleType();
  }
}
