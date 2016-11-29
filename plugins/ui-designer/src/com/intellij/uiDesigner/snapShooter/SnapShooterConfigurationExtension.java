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

import com.intellij.designer.DesignerEditorPanelFacade;
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
import com.intellij.ide.palette.PaletteGroup;
import com.intellij.ide.ui.LafManagerListener;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.BaseComponent;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.extensions.AreaInstance;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.pom.Navigatable;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.lw.LwComponent;
import com.intellij.util.PathUtil;
import com.intellij.util.net.NetUtils;
import com.intellij.xml.util.XmlStringUtil;
import com.jgoodies.forms.layout.FormLayout;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import java.util.Set;
import java.util.TreeSet;

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
      Set<String> paths = new TreeSet<>();
      paths.add(PathUtil.getJarPathForClass(SnapShooter.class));               // ui-designer-impl
      paths.add(PathUtil.getJarPathForClass(BaseComponent.class));             // appcore-api
      paths.add(PathUtil.getJarPathForClass(ProjectComponent.class));          // openapi
      paths.add(PathUtil.getJarPathForClass(DesignerEditorPanelFacade.class)); // platform-impl
      paths.add(PathUtil.getJarPathForClass(LwComponent.class));               // UIDesignerCore
      paths.add(PathUtil.getJarPathForClass(GridConstraints.class));           // forms_rt
      paths.add(PathUtil.getJarPathForClass(PaletteGroup.class));              // openapi
      paths.add(PathUtil.getJarPathForClass(LafManagerListener.class));        // ui-impl
      paths.add(PathUtil.getJarPathForClass(DataProvider.class));              // action-system-openapi
      paths.add(PathUtil.getJarPathForClass(XmlStringUtil.class));             // idea
      paths.add(PathUtil.getJarPathForClass(Navigatable.class));               // pom
      paths.add(PathUtil.getJarPathForClass(AreaInstance.class));              // extensions
      paths.add(PathUtil.getJarPathForClass(FormLayout.class));                // jgoodies
      paths.addAll(PathManager.getUtilClassPath());
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
