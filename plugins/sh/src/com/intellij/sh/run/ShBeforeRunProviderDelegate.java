// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.run;

import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.impl.RunConfigurationBeforeRunProviderDelegate;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

public class ShBeforeRunProviderDelegate implements RunConfigurationBeforeRunProviderDelegate {
  private static final String SH_BEFORE_KEY_PREFIX = "Sh.BeforeRunTask";
  private static final Map<String, Key<Boolean>> KEY_MAP = new HashMap<>();

  @Override
  public void beforeRun(@NotNull ExecutionEnvironment environment) {
    RunnerAndConfigurationSettings settings = environment.getRunnerAndConfigurationSettings();
    if (settings == null) return;
    RunConfiguration configuration = settings.getConfiguration();
    if (configuration instanceof ShRunConfiguration) {
      Key<Boolean> userDataKey = getRunBeforeUserDataKey(configuration);
      configuration.getProject().putUserData(userDataKey, true);
    }
  }

  public static Key<Boolean> getRunBeforeUserDataKey(@NotNull RunConfiguration runConfiguration) {
    return KEY_MAP.computeIfAbsent(runConfiguration.getName(), key -> Key.create(SH_BEFORE_KEY_PREFIX + "_" + key));
  }
}