// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.uiDesigner.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.service.JpsServiceManager;

public abstract class JpsUiDesignerExtensionService {
  public static JpsUiDesignerExtensionService getInstance() {
    return JpsServiceManager.getInstance().getService(JpsUiDesignerExtensionService.class);
  }

  public abstract @Nullable JpsUiDesignerConfiguration getUiDesignerConfiguration(@NotNull JpsProject project);

  public abstract void setUiDesignerConfiguration(@NotNull JpsProject project, @NotNull JpsUiDesignerConfiguration configuration);

  public abstract @NotNull JpsUiDesignerConfiguration getOrCreateUiDesignerConfiguration(@NotNull JpsProject project);
}
