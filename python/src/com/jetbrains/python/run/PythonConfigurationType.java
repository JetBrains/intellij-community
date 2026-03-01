// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.run;

import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.ConfigurationTypeUtil;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.project.Project;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.parser.icons.PythonParserIcons;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.Icon;


public final class PythonConfigurationType implements ConfigurationType {

  private final PythonConfigurationFactory myFactory = new PythonConfigurationFactory(this);

  public static PythonConfigurationType getInstance() {
    return ConfigurationTypeUtil.findConfigurationType(PythonConfigurationType.class);
  }

  public static class PythonConfigurationFactory extends PythonConfigurationFactoryBase {
    protected PythonConfigurationFactory(ConfigurationType configurationType) {
      super(configurationType);
    }

    @Override
    public @NotNull RunConfiguration createTemplateConfiguration(@NotNull Project project) {
      return new PythonRunConfiguration(project, this);
    }

    @Override
    public @NotNull String getId() {
      return "Python";
    }
  }

  @Override
  public @NotNull String getDisplayName() {
    return PyBundle.message("python.run.python");
  }

  @Override
  public String getConfigurationTypeDescription() {
    return PyBundle.message("python.run.configuration");
  }

  @Override
  public Icon getIcon() {
    return  PythonParserIcons.PythonFile;
  }

  @Override
  public ConfigurationFactory[] getConfigurationFactories() {
    return new ConfigurationFactory[]{myFactory};
  }

  @Override
  public String getHelpTopic() {
    return "reference.dialogs.rundebug.PythonConfigurationType";
  }

  public PythonConfigurationFactory getFactory() {
    return myFactory;
  }

  @Override
  public @NotNull @NonNls String getId() {
    return "PythonConfigurationType";
  }

  @Override
  public boolean isDumbAware() {
    return true;
  }
}
