package org.jetbrains.jps.uiDesigner.model.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.uiDesigner.model.JpsUiDesignerConfiguration;
import org.jetbrains.jps.uiDesigner.model.JpsUiDesignerExtensionService;

/**
 * @author nik
 */
public class JpsUiDesignerExtensionServiceImpl extends JpsUiDesignerExtensionService {
  @Nullable
  @Override
  public JpsUiDesignerConfiguration getUiDesignerConfiguration(@NotNull JpsProject project) {
    return project.getContainer().getChild(JpsUiDesignerConfigurationImpl.ROLE);
  }

  @NotNull
  @Override
  public JpsUiDesignerConfiguration getOrCreateUiDesignerConfiguration(@NotNull JpsProject project) {
    JpsUiDesignerConfiguration config = project.getContainer().getChild(JpsUiDesignerConfigurationImpl.ROLE);
    if (config == null) {
      config = new JpsUiDesignerConfigurationImpl();
      setUiDesignerConfiguration(project, config);
    }
    return config;
  }

  @Override
  public void setUiDesignerConfiguration(@NotNull JpsProject project, @NotNull JpsUiDesignerConfiguration configuration) {
    project.getContainer().setChild(JpsUiDesignerConfigurationImpl.ROLE, configuration);
  }
}
