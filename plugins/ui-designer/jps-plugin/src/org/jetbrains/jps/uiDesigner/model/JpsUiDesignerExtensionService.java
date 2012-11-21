package org.jetbrains.jps.uiDesigner.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.service.JpsServiceManager;

/**
 * @author nik
 */
public abstract class JpsUiDesignerExtensionService {
  public static JpsUiDesignerExtensionService getInstance() {
    return JpsServiceManager.getInstance().getService(JpsUiDesignerExtensionService.class);
  }

  @Nullable
  public abstract JpsUiDesignerConfiguration getUiDesignerConfiguration(@NotNull JpsProject project);

  public abstract void setUiDesignerConfiguration(@NotNull JpsProject project, @NotNull JpsUiDesignerConfiguration configuration);

  @NotNull
  public abstract JpsUiDesignerConfiguration getOrCreateUiDesignerConfiguration(@NotNull JpsProject project);
}
