/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.execution;

import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.impl.RunnerAndConfigurationSettings;
import com.intellij.openapi.project.Project;

/**
 * Manages {@link RunConfiguration}s.
 *
 * @see ExecutionRegistry
 * @see ExecutionManager
 */
public abstract class RunManager {
  public static RunManager getInstance(final Project project) {
    return project.getComponent(RunManager.class);
  }

  public abstract ConfigurationType getActiveConfigurationFactory();

  public abstract ConfigurationType[] getConfigurationFactories();

  public abstract RunConfiguration[] getConfigurations(ConfigurationType type);

  public abstract RunConfiguration[] getAllConfigurations();

  public abstract RunnerAndConfigurationSettings getSelectedConfiguration(ConfigurationType type);

  public abstract RunnerAndConfigurationSettings getSelectedConfiguration();

  public abstract RunConfiguration getTempConfiguration();

  public abstract boolean isTemporary(RunConfiguration configuration);
  public abstract boolean isTemporary(RunnerAndConfigurationSettings configuration);

  public abstract void makeStable(RunConfiguration configuration);

  public abstract void setActiveConfiguration(RunnerAndConfigurationSettings configuration);

  public abstract void setActiveConfigurationFactory(ConfigurationType activeConfigurationType);

  public abstract void setSelectedConfiguration(RunnerAndConfigurationSettings configuration);

  public abstract void setTemporaryConfiguration(RunnerAndConfigurationSettings tempConfiguration);

  public abstract RunManagerConfig getConfig();

  public abstract RunnerAndConfigurationSettings createConfiguration(String name, ConfigurationFactory type);

  public abstract RunnerAndConfigurationSettings[] getConfigurationSettings(ConfigurationType type);

  public abstract void addConfiguration(RunnerAndConfigurationSettings settings);
}