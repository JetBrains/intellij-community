// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.ipnb.run;

import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.ConfigurationTypeUtil;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.project.Project;
import com.jetbrains.python.run.PythonConfigurationFactoryBase;
import icons.PythonIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public final class IpnbRunConfigurationType implements ConfigurationType {
  public final IpnbRunConfigurationFactory IPNB_FACTORY = new IpnbRunConfigurationFactory(this);

  public static IpnbRunConfigurationType getInstance() {
    return ConfigurationTypeUtil.findConfigurationType(IpnbRunConfigurationType.class);
  }

  @NotNull
  @Override
  public String getDisplayName() {
    return "Jupyter Notebook";
  }

  @Override
  public String getConfigurationTypeDescription() {
    return "Jupyter Notebook";
  }

  @Override
  public Icon getIcon() {
    return PythonIcons.Python.IpythonNotebook;
  }

  @Override
  @NotNull
  public String getId() {
    return "JupiterNotebook";
  }

  @Override
  public ConfigurationFactory[] getConfigurationFactories() {
    return new ConfigurationFactory[]{IPNB_FACTORY};
  }

  @Override
  public String getHelpTopic() {
    return "reference.dialogs.rundebug.JupiterNotebook";
  }

  private static class IpnbRunConfigurationFactory extends PythonConfigurationFactoryBase {
    public IpnbRunConfigurationFactory(@NotNull ConfigurationType type) {
      super(type);
    }

    @Override
    @NotNull
    public RunConfiguration createTemplateConfiguration(@NotNull Project project) {
      return new IpnbRunConfiguration(project, this);
    }

    @NotNull
    @Override
    public String getName() {
      return "Jupyter Notebook";
    }
  }
}
