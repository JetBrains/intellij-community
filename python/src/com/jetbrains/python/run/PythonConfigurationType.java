// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.run;

import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.ConfigurationTypeUtil;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.project.Project;
import icons.PythonIcons;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author yole
 */
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
    @NotNull
    public RunConfiguration createTemplateConfiguration(@NotNull Project project) {
      return new PythonRunConfiguration(project, this);
    }
  }

  @NotNull
  @Override
  public String getDisplayName() {
    return "Python";
  }

  @Override
  public String getConfigurationTypeDescription() {
    return "Python run configuration";
  }

  @Override
  public Icon getIcon() {
    return PythonIcons.Python.Python;
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
  @NotNull
  @NonNls
  public String getId() {
    return "PythonConfigurationType";
  }

  @Override
  public boolean isDumbAware() {
    return true;
  }
}
