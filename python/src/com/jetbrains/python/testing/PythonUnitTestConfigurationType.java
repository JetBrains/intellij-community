package com.jetbrains.python.testing;

import com.intellij.execution.LocatableConfigurationType;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.Location;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.jetbrains.python.PyBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class PythonUnitTestConfigurationType implements LocatableConfigurationType {
  public static PythonUnitTestConfigurationType getInstance() {
    for (ConfigurationType configType : Extensions.getExtensions(CONFIGURATION_TYPE_EP)) {
      if (configType instanceof PythonUnitTestConfigurationType) {
        return (PythonUnitTestConfigurationType)configType;
      }
    }
    assert false;
    return null;
  }

  private final static Icon ICON = IconLoader.getIcon("/com/jetbrains/python/python.png");

  public RunnerAndConfigurationSettings createConfigurationByLocation(Location location) {
    return null;
  }

  public boolean isConfigurationByLocation(RunConfiguration configuration, Location location) {
    return false;
  }

  private static class PythonConfigurationFactory extends ConfigurationFactory {
    protected PythonConfigurationFactory(ConfigurationType configurationType) {
      super(configurationType);
    }

    public RunConfiguration createTemplateConfiguration(Project project) {
      return new PythonUnitTestRunConfiguration(project, this, "");
    }
  }

  public String getDisplayName() {
    return PyBundle.message("runcfg.unittest.display_name");
  }

  public String getConfigurationTypeDescription() {
    return PyBundle.message("runcfg.unittest.description");
  }

  public Icon getIcon() {
    return ICON;
  }

  public ConfigurationFactory[] getConfigurationFactories() {
    return new ConfigurationFactory[]{new PythonConfigurationFactory(this)};
  }

  @NotNull
  @NonNls
  public String getId() {
    return "PythonUnitTestConfigurationType";
  }
}
