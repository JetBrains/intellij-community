// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.module;

import com.intellij.openapi.module.ModuleType;
import com.jetbrains.python.PythonModuleTypeBase;
import org.jetbrains.annotations.ApiStatus;

/**
 * @deprecated use {@link PythonModuleBuilderBase}
 */
@Deprecated(forRemoval = true)
@ApiStatus.Obsolete
public final class PythonModuleType {
  private PythonModuleType() { }

  /**
   * @deprecated use {@link PythonModuleBuilderBase}
   */
  @Deprecated(forRemoval = true)
  @ApiStatus.Obsolete
  public static ModuleType getInstance() {
    return PythonModuleTypeBase.getInstance();
  }
}

