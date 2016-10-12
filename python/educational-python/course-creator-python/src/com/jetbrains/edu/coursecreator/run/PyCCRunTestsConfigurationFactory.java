package com.jetbrains.edu.coursecreator.run;

import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class PyCCRunTestsConfigurationFactory extends ConfigurationFactory {
  protected PyCCRunTestsConfigurationFactory(@NotNull ConfigurationType type) {
    super(type);
  }

  @NotNull
  @Override
  public RunConfiguration createTemplateConfiguration(@NotNull Project project) {
    return new PyCCRunTestConfiguration(project, this);
  }
}
