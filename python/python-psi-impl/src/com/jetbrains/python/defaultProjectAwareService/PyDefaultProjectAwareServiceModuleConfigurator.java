// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.defaultProjectAwareService;

import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.NotNull;

public interface PyDefaultProjectAwareServiceModuleConfigurator {
  /**
   * Configures newly created module
   * @param newProject true if new project created
   */
  void configureModule(@NotNull Module module, boolean newProject);
}
