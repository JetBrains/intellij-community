package com.jetbrains.python.run;

import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import icons.PythonIcons;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author yole
 */
public class PythonConfigurationType implements ConfigurationType {

  private final PythonConfigurationFactory myFactory = new PythonConfigurationFactory(this);

  public static PythonConfigurationType getInstance() {
    for(ConfigurationType configType: Extensions.getExtensions(CONFIGURATION_TYPE_EP)) {
      if (configType instanceof PythonConfigurationType) {
        return (PythonConfigurationType) configType;
      }
    }
    assert false;
    return null;
  }

  private static class PythonConfigurationFactory extends ConfigurationFactory {
    protected PythonConfigurationFactory(ConfigurationType configurationType) {
      super(configurationType);
    }

    public RunConfiguration createTemplateConfiguration(Project project) {
      return new PythonRunConfiguration(project, this);
    }
  }

  public String getDisplayName() {
    return "Python";
  }

  public String getConfigurationTypeDescription() {
    return "Python run configuration";
  }

  public Icon getIcon() {
    return PythonIcons.Python.Python;
  }

  public ConfigurationFactory[] getConfigurationFactories() {
    return new ConfigurationFactory[]{myFactory};
  }

  public PythonConfigurationFactory getFactory() {
    return myFactory;
  }

  @NotNull
  @NonNls
  public String getId() {
    return "PythonConfigurationType";
  }
}
