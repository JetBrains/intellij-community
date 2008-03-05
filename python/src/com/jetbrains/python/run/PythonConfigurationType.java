package com.jetbrains.python.run;

import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author yole
 */
public class PythonConfigurationType implements ConfigurationType {
  public static PythonConfigurationType getInstance() {
    for(ConfigurationType configType: Extensions.getExtensions(CONFIGURATION_TYPE_EP)) {
      if (configType instanceof PythonConfigurationType) {
        return (PythonConfigurationType) configType;
      }
    }
    assert false;
    return null;
  }

  private Icon _icon = IconLoader.getIcon("/com/jetbrains/python/python.png");

  private static class PythonConfigurationFactory extends ConfigurationFactory {
    protected PythonConfigurationFactory(ConfigurationType configurationType) {
      super(configurationType);
    }

    public RunConfiguration createTemplateConfiguration(Project project) {
      return new PythonRunConfiguration(project, this, "");
    }
  }

  public String getDisplayName() {
    return "Python";
  }

  public String getConfigurationTypeDescription() {
    return "Python run configuration";
  }

  public Icon getIcon() {
    return _icon;
  }

  public ConfigurationFactory[] getConfigurationFactories() {
    return new ConfigurationFactory[]{new PythonConfigurationFactory(this)};
  }

  @NotNull
  @NonNls
  public String getId() {
    return "PythonConfigurationType";
  }
}
