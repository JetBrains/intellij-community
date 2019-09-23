// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.run;

import com.intellij.execution.BeforeRunTask;
import com.intellij.execution.BeforeRunTaskProvider;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.compound.ConfigurationSelectionUtil;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.impl.RunConfigurationBeforeRunProvider;
import com.intellij.execution.impl.RunManagerImpl;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.sh.ShLanguage;
import com.intellij.util.containers.ContainerUtil;
import icons.SHIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.concurrency.Promise;

import javax.swing.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ShBeforeRunTaskProvider extends BeforeRunTaskProvider<ShBeforeRunTaskProvider.ShBeforeRunTask> {
  private static final String SH_BEFORE_KEY_PREFIX = "Sh.BeforeRunTask";
  private static final Key<ShBeforeRunTask> ID = Key.create(SH_BEFORE_KEY_PREFIX);
  private static final Map<String, Key<Boolean>> KEY_MAP = new HashMap<>();

  @Override
  public Key<ShBeforeRunTask> getId() {
    return ID;
  }

  @Override
  public String getName() {
    return "Run " + ShLanguage.INSTANCE.getID();
  }

  @Override
  public String getDescription(ShBeforeRunTask task) {
    RunnerAndConfigurationSettings configurationSettings = task.getSettings();
    return configurationSettings != null ? "Run " + ShLanguage.INSTANCE.getID() + " '" + configurationSettings.getName() + "'"
                                         : "Run " + ShLanguage.INSTANCE.getID();
  }

  @Nullable
  @Override
  public Icon getIcon() {
    return SHIcons.ShFile;
  }

  @Nullable
  @Override
  public Icon getTaskIcon(ShBeforeRunTask task) {
    return SHIcons.ShFile;
  }

  @Nullable
  @Override
  public ShBeforeRunTask createTask(@NotNull RunConfiguration runConfiguration) {
    return new ShBeforeRunTask(runConfiguration.getProject());
  }

  @Override
  public boolean isConfigurable() {
    return true;
  }

  @Override
  public Promise<Boolean> configureTask(@NotNull DataContext context,
                                        @NotNull RunConfiguration currentConfig,
                                        @NotNull ShBeforeRunTask task) {
    AsyncPromise<Boolean> result = new AsyncPromise<>();
    Project project = currentConfig.getProject();
    if (project == null || !project.isInitialized()) {
      result.setResult(false);
      return result;
    }

    RunManagerImpl runManager = RunManagerImpl.getInstanceImpl(project);
    List<RunConfiguration> configurations = RunManagerImpl.getInstanceImpl(project).getAllSettings().stream()
      .map(RunnerAndConfigurationSettings::getConfiguration)
      .filter(config -> config instanceof ShRunConfiguration && config != currentConfig)
      .collect(Collectors.toList());

    ConfigurationSelectionUtil.createPopup(project, runManager, configurations, (selectedConfigs, selectedTarget) -> {
      RunConfiguration selectedConfig = ContainerUtil.getFirstItem(selectedConfigs);
      RunnerAndConfigurationSettings selectedSettings = selectedConfig == null ? null : runManager.getSettings(selectedConfig);

      if (selectedSettings != null) {
        task.setSettings(selectedSettings);
        result.setResult(true);
      }
      else {
        result.setResult(false);
      }
    }).showInBestPositionFor(context);
    return result;
  }

  @Override
  public boolean executeTask(@NotNull DataContext context,
                             @NotNull RunConfiguration configuration,
                             @NotNull ExecutionEnvironment env,
                             @NotNull ShBeforeRunTask task) {
    RunnerAndConfigurationSettings configurationSettings = task.getSettings();
    if (configurationSettings == null) return false;

    Key<Boolean> userDataKey = getRunBeforeUserDataKey(configurationSettings.getConfiguration());
    configuration.getProject().putUserData(userDataKey, true);
    boolean result = RunConfigurationBeforeRunProvider.doExecuteTask(env, configurationSettings, null);
    configuration.getProject().putUserData(userDataKey, false);
    return result;
  }

  static Key<Boolean> getRunBeforeUserDataKey(@NotNull RunConfiguration runConfiguration) {
    return KEY_MAP.computeIfAbsent(runConfiguration.getName(), key -> Key.create(SH_BEFORE_KEY_PREFIX + "_" + key));
  }

  static class ShBeforeRunTask extends BeforeRunTask<ShBeforeRunTask> implements PersistentStateComponent<ShBeforeRunTaskState> {
    private ShBeforeRunTaskState myState = new ShBeforeRunTaskState();
    private RunnerAndConfigurationSettings mySettings;
    private final Project myProject;

    protected ShBeforeRunTask(@NotNull Project project) {
      super(ID);
      myProject = project;
    }

    @Nullable
    @Override
    public ShBeforeRunTaskState getState() {
      return myState;
    }

    @Override
    public void loadState(@NotNull ShBeforeRunTaskState state) {
      state.resetModificationCount();
      myState = state;
    }

    public void setSettings(@Nullable RunnerAndConfigurationSettings settings) {
      mySettings = settings;
      if (settings == null) {
        myState.setRunConfigType("");
        myState.setRunConfigName("");
        return;
      }
      myState.setRunConfigType(settings.getType().getId());
      myState.setRunConfigName(settings.getName());
    }

    @Nullable
    public RunnerAndConfigurationSettings getSettings() {
      if (mySettings != null) return mySettings;
      return myState != null && myState.getRunConfigType() != null && myState.getRunConfigName() != null
             ? mySettings = RunManagerImpl.getInstanceImpl(myProject).findConfigurationByTypeAndName(myState.getRunConfigType(), myState.getRunConfigName())
             : null;
    }
  }
}
