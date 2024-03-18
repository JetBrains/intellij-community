// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.plugin.java.facet;

import com.jetbrains.python.PythonModuleTypeBase;
import com.jetbrains.python.module.PythonModuleBuilderBase;
import org.jetbrains.annotations.NotNull;


final class PythonModuleType extends PythonModuleTypeBase<PythonModuleBuilderBase> {
  @Override
  @NotNull
  public PythonModuleBuilder createModuleBuilder() {
    return new PythonModuleBuilder();
  }
}
