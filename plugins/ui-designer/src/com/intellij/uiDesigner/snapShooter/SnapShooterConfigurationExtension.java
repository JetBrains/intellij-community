/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.uiDesigner.snapShooter;

import com.intellij.execution.Location;
import com.intellij.execution.RunConfigurationExtension;
import com.intellij.execution.application.ApplicationConfiguration;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.execution.configurations.RunnerSettings;
import com.intellij.execution.configurations.RuntimeConfigurationException;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.util.net.NetUtils;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.platform.loader.PlatformLoader;
import org.jetbrains.platform.loader.repository.RuntimeModuleId;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * @author yole
 */
public class SnapShooterConfigurationExtension extends RunConfigurationExtension {
  @Override
  public void updateJavaParameters(RunConfigurationBase configuration, JavaParameters params, RunnerSettings runnerSettings) {
    if (!isApplicableFor(configuration)) {
      return;
    }
    ApplicationConfiguration appConfiguration = (ApplicationConfiguration) configuration;
    SnapShooterConfigurationSettings settings = appConfiguration.getUserData(SnapShooterConfigurationSettings.SNAP_SHOOTER_KEY);
    if (settings == null) {
      settings = new SnapShooterConfigurationSettings();
      appConfiguration.putUserData(SnapShooterConfigurationSettings.SNAP_SHOOTER_KEY, settings);
    }
    if (appConfiguration.ENABLE_SWING_INSPECTOR) {
      settings.setLastPort(NetUtils.tryToFindAvailableSocketPort());
    }

    if (appConfiguration.ENABLE_SWING_INSPECTOR && settings.getLastPort() != -1) {
      params.getProgramParametersList().prepend(appConfiguration.MAIN_CLASS_NAME);
      params.getProgramParametersList().prepend(Integer.toString(settings.getLastPort()));
      // add +1 because idea_rt.jar will be added as the last entry to the classpath
      params.getProgramParametersList().prepend(Integer.toString(params.getClassPath().getPathList().size() + 1));
      RuntimeModuleId[] modules = {
        RuntimeModuleId.module("ui-designer"),
        RuntimeModuleId.module("ui-designer-core"),
        RuntimeModuleId.module("core-api"),
        RuntimeModuleId.module("openapi"),
        RuntimeModuleId.module("forms_rt"),
        RuntimeModuleId.module("forms-compiler"),
        RuntimeModuleId.module("platform-api"),
        RuntimeModuleId.module("editor-ui-api"),
        RuntimeModuleId.module("extensions"),
        RuntimeModuleId.projectLibrary("jgoodies-forms"),
      };
      Set<String> paths = new LinkedHashSet<String>();
      for (RuntimeModuleId module : modules) {
        paths.addAll(PlatformLoader.getInstance().getRepository().getModuleRootPaths(module));
      }
      for(String path: paths) {
        params.getClassPath().addFirst(path);
      }
      params.setMainClass("com.intellij.uiDesigner.snapShooter.SnapShooter");
    }
  }

  protected boolean isApplicableFor(@NotNull RunConfigurationBase configuration) {
    return configuration instanceof ApplicationConfiguration;
  }

  public void attachToProcess(@NotNull final RunConfigurationBase configuration, @NotNull final ProcessHandler handler, RunnerSettings runnerSettings) {
    SnapShooterConfigurationSettings settings = configuration.getUserData(SnapShooterConfigurationSettings.SNAP_SHOOTER_KEY);
    if (settings != null) {
      final Runnable runnable = settings.getNotifyRunnable();
      if (runnable != null) {
        settings.setNotifyRunnable(null);
        handler.addProcessListener(new ProcessAdapter() {
          public void startNotified(final ProcessEvent event) {
            runnable.run();
          }
        });
      }
    }
  }

  @Override
  public SettingsEditor createEditor(@NotNull RunConfigurationBase configuration) {
    return null;
  }

  @Override
  public String getEditorTitle() {
    return null;
  }

  @NotNull
  @Override
  public String getSerializationId() {
    return "snapshooter";
  }

  @Override
  public void readExternal(@NotNull RunConfigurationBase runConfiguration, @NotNull Element element) throws InvalidDataException {
  }

  @Override
  public void writeExternal(@NotNull RunConfigurationBase runConfiguration, @NotNull Element element) throws WriteExternalException {
    throw new WriteExternalException();
  }

  @Override
  public void extendCreatedConfiguration(@NotNull RunConfigurationBase runJavaConfiguration, @NotNull Location location) {
  }

  @Override
  public void validateConfiguration(@NotNull RunConfigurationBase runJavaConfiguration, boolean isExecution)
    throws RuntimeConfigurationException {

  }
}
