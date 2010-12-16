package com.jetbrains.python.testing.nosetest;

import com.intellij.execution.configurations.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.jetbrains.python.PyBundle;

import javax.swing.*;

/**
 * User: catherine
 */
public class PythonNoseTestRunConfigurationType extends ConfigurationTypeBase {
  public static PythonNoseTestRunConfigurationType getInstance() {
    return ConfigurationTypeUtil.findConfigurationType(PythonNoseTestRunConfigurationType.class);
  }

  public PythonNoseTestRunConfigurationType() {
    super("PythonNoseTestRunConfigurationType",
          PyBundle.message("runcfg.nosetests.display_name"),
          PyBundle.message("runcfg.nosetests.description"),
          ICON);
    addFactory(new PythonNoseTestConfigurationFactory(this));
  }

  private final static Icon ICON = IconLoader.getIcon("/com/jetbrains/python/icons/python.png");

  private static class PythonNoseTestConfigurationFactory extends ConfigurationFactory {
    protected PythonNoseTestConfigurationFactory(ConfigurationType configurationType) {
      super(configurationType);
    }

    public RunConfiguration createTemplateConfiguration(Project project) {
      return new PythonNoseTestRunConfiguration(new RunConfigurationModule(project), this, "");
    }
  }
}
