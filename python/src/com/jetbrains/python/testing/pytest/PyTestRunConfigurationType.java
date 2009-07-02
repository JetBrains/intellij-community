package com.jetbrains.python.testing.pytest;

import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.RunConfigurationModule;
import com.intellij.openapi.project.Project;
import com.jetbrains.python.PythonFileType;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author yole
 */
public class PyTestRunConfigurationType implements ConfigurationType {
  private ConfigurationFactory myPyTestConfigurationFactory = new PyTestRunConfigurationFactory(this);

  public String getDisplayName() {
    return "py.test";
  }

  public String getConfigurationTypeDescription() {
    return "py.test";
  }

  public Icon getIcon() {
    return PythonFileType.INSTANCE.getIcon();
  }

  @NotNull
  public String getId() {
    return "py.test";
  }

  public ConfigurationFactory[] getConfigurationFactories() {
    return new ConfigurationFactory[] { myPyTestConfigurationFactory };
  }

  private static class PyTestRunConfigurationFactory extends ConfigurationFactory {
    protected PyTestRunConfigurationFactory(ConfigurationType type) {
      super(type);
    }

    public RunConfiguration createTemplateConfiguration(Project project) {
      return new PyTestRunConfiguration("", new RunConfigurationModule(project), this);
    }
  }
}
