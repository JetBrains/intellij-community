// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.module;

import com.jetbrains.python.PythonModuleTypeBase;
import org.jetbrains.annotations.NotNull;


public final class PythonModuleType extends PythonModuleTypeBase<PythonModuleBuilderBase> {
  @Override
  @NotNull
  public PythonModuleBuilder createModuleBuilder() {
    return new PythonModuleBuilder();
  }
}
