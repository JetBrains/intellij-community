package com.jetbrains.python.testing.attest;

import com.intellij.execution.configurations.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.jetbrains.python.PyBundle;

import javax.swing.*;

/**
 * User: catherine
 */
public class PythonAtTestRunConfigurationType extends ConfigurationTypeBase {
  public static PythonAtTestRunConfigurationType getInstance() {
    return ConfigurationTypeUtil.findConfigurationType(PythonAtTestRunConfigurationType.class);
  }

  public PythonAtTestRunConfigurationType() {
    super("PythonAtTestRunConfigurationType",
          PyBundle.message("runcfg.attest.display_name"),
          PyBundle.message("runcfg.attest.description"),
          ICON);
    addFactory(new PythonAtTestConfigurationFactory(this));
  }

  private final static Icon ICON = IconLoader.getIcon("/com/jetbrains/python/icons/python.png");

  private static class PythonAtTestConfigurationFactory extends ConfigurationFactory {
    protected PythonAtTestConfigurationFactory(ConfigurationType configurationType) {
      super(configurationType);
    }

    public RunConfiguration createTemplateConfiguration(Project project) {
      return new PythonAtTestRunConfiguration(new RunConfigurationModule(project), this, "");
    }
  }
}
