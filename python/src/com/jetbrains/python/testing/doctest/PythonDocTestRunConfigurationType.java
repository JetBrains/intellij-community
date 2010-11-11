package com.jetbrains.python.testing.doctest;

import com.intellij.execution.configurations.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.jetbrains.python.PyBundle;

import javax.swing.*;

/**
 * User: catherine
 */
public class PythonDocTestRunConfigurationType extends ConfigurationTypeBase {
  public static PythonDocTestRunConfigurationType getInstance() {
    return ConfigurationTypeUtil.findConfigurationType(PythonDocTestRunConfigurationType.class);
  }

  public PythonDocTestRunConfigurationType() {
    super("PythonDocTestRunConfigurationType",
          PyBundle.message("runcfg.doctest.display_name"),
          PyBundle.message("runcfg.doctest.description"),
          ICON);
    addFactory(new PythonDocTestConfigurationFactory(this));
  }

  private final static Icon ICON = IconLoader.getIcon("/com/jetbrains/python/icons/python.png");

  private static class PythonDocTestConfigurationFactory extends ConfigurationFactory {
    protected PythonDocTestConfigurationFactory(ConfigurationType configurationType) {
      super(configurationType);
    }

    public RunConfiguration createTemplateConfiguration(Project project) {
      return new PythonDocTestRunConfiguration(new RunConfigurationModule(project), this, "");
    }
  }
}
