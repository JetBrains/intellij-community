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

public class IpnbRunConfigurationType implements ConfigurationType {
  public final IpnbRunConfigurationFactory IPNB_FACTORY = new IpnbRunConfigurationFactory(this);

  public static IpnbRunConfigurationType getInstance() {
    return ConfigurationTypeUtil.findConfigurationType(IpnbRunConfigurationType.class);
  }

  public String getDisplayName() {
    return "Jupyter Notebook";
  }

  public String getConfigurationTypeDescription() {
    return "Jupyter Notebook";
  }

  public Icon getIcon() {
    return PythonIcons.Python.IpythonNotebook;
  }

  @NotNull
  public String getId() {
    return "JupiterNotebook";
  }

  public ConfigurationFactory[] getConfigurationFactories() {
    return new ConfigurationFactory[]{IPNB_FACTORY};
  }

  private static class IpnbRunConfigurationFactory extends PythonConfigurationFactoryBase {
    public IpnbRunConfigurationFactory(@NotNull ConfigurationType type) {
      super(type);
    }

    @Override
    public boolean isConfigurationSingletonByDefault() {
      return true;
    }

    @NotNull
    public RunConfiguration createTemplateConfiguration(@NotNull Project project) {
      return new IpnbRunConfiguration(project, this);
    }

    @Override
    public String getName() {
      return "Jupyter Notebook";
    }
  }
}
