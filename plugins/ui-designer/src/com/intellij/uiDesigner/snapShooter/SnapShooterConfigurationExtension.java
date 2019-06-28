// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.uiDesigner.snapShooter;

import com.intellij.designer.DesignerEditorPanelFacade;
import com.intellij.execution.RunConfigurationExtension;
import com.intellij.execution.application.ApplicationConfiguration;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.execution.configurations.RunnerSettings;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.ide.palette.PaletteGroup;
import com.intellij.ide.ui.LafManagerListener;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.BaseComponent;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.extensions.AreaInstance;
import com.intellij.pom.Navigatable;
import com.intellij.ui.ColoredTextContainer;
import com.intellij.ui.JBColor;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.lw.LwComponent;
import com.intellij.util.PathUtil;
import com.intellij.util.lang.UrlClassLoader;
import com.intellij.util.net.NetUtils;
import com.intellij.util.ui.UIUtilities;
import com.intellij.xml.util.XmlStringUtil;
import com.jgoodies.forms.layout.FormLayout;
import kotlin.TypeCastException;
import org.jetbrains.annotations.NotNull;

import java.util.Set;
import java.util.TreeSet;

/**
 * @author yole
 */
public class SnapShooterConfigurationExtension extends RunConfigurationExtension {
  @Override
  public void updateJavaParameters(@NotNull RunConfigurationBase configuration, @NotNull JavaParameters params, RunnerSettings runnerSettings) {
    if (!isApplicableFor(configuration)) {
      return;
    }
    ApplicationConfiguration appConfiguration = (ApplicationConfiguration)configuration;
    SnapShooterConfigurationSettings settings = appConfiguration.getUserData(SnapShooterConfigurationSettings.SNAP_SHOOTER_KEY);
    if (settings == null) {
      settings = new SnapShooterConfigurationSettings();
      appConfiguration.putUserData(SnapShooterConfigurationSettings.SNAP_SHOOTER_KEY, settings);
    }
    boolean swingInspectorEnabled = appConfiguration.isSwingInspectorEnabled();
    if (swingInspectorEnabled) {
      settings.setLastPort(NetUtils.tryToFindAvailableSocketPort());
    }

    if (swingInspectorEnabled && settings.getLastPort() != -1) {
      params.getProgramParametersList().prepend(appConfiguration.getMainClassName());
      params.getProgramParametersList().prepend(Integer.toString(settings.getLastPort()));
      Set<String> paths = new TreeSet<>();
      paths.add(PathUtil.getJarPathForClass(SnapShooter.class));               // intellij.java.guiForms.designer
      paths.add(PathUtil.getJarPathForClass(BaseComponent.class));             // intellij.platform.core
      paths.add(PathUtil.getJarPathForClass(ProjectComponent.class));          // intellij.java
      paths.add(PathUtil.getJarPathForClass(DesignerEditorPanelFacade.class)); // intellij.platform.ide.impl
      paths.add(PathUtil.getJarPathForClass(LwComponent.class));               // intellij.java.guiForms.compiler
      paths.add(PathUtil.getJarPathForClass(GridConstraints.class));           // intellij.java.guiForms.rt
      paths.add(PathUtil.getJarPathForClass(PaletteGroup.class));              // intellij.java.guiForms.designer
      paths.add(PathUtil.getJarPathForClass(LafManagerListener.class));        // intellij.platform.ide
      paths.add(PathUtil.getJarPathForClass(DataProvider.class));              // intellij.platform.editor
      paths.add(PathUtil.getJarPathForClass(XmlStringUtil.class));             // intellij.platform.util
      paths.add(PathUtil.getJarPathForClass(UrlClassLoader.class));            // intellij.platform.util.classLoader
      paths.add(PathUtil.getJarPathForClass(Navigatable.class));               // intellij.platform.core
      paths.add(PathUtil.getJarPathForClass(AreaInstance.class));              // intellij.platform.extensions
      paths.add(PathUtil.getJarPathForClass(FormLayout.class));                // jgoodies
      paths.add(PathUtil.getJarPathForClass(PersistentStateComponent.class));  // intellij.platform.projectModel
      paths.add(PathUtil.getJarPathForClass(JBColor.class));                   // intellij.platform.util.ui
      paths.add(PathUtil.getJarPathForClass(ColoredTextContainer.class));      // intellij.platform.core.ui
      paths.add(PathUtil.getJarPathForClass(UIUtilities.class));               // Java Compatibility library
      paths.add(PathUtil.getJarPathForClass(TypeCastException.class));         // kotlin
      paths.addAll(PathManager.getUtilClassPath());
      for(String path: paths) {
        params.getClassPath().addFirst(path);
      }
      // add +1 because idea_rt.jar will be added as the last entry to the classpath
      params.getProgramParametersList().prepend(Integer.toString(params.getClassPath().getPathList().size() + 1));
      params.setMainClass("com.intellij.uiDesigner.snapShooter.SnapShooter");
    }
  }

  @Override
  public boolean isApplicableFor(@NotNull RunConfigurationBase configuration) {
    return configuration instanceof ApplicationConfiguration;
  }

  @Override
  public void attachToProcess(@NotNull final RunConfigurationBase configuration, @NotNull final ProcessHandler handler, RunnerSettings runnerSettings) {
    SnapShooterConfigurationSettings settings = configuration.getUserData(SnapShooterConfigurationSettings.SNAP_SHOOTER_KEY);
    if (settings != null) {
      final Runnable runnable = settings.getNotifyRunnable();
      if (runnable != null) {
        settings.setNotifyRunnable(null);
        handler.addProcessListener(new ProcessAdapter() {
          @Override
          public void startNotified(@NotNull final ProcessEvent event) {
            runnable.run();
          }
        });
      }
    }
  }

  @NotNull
  @Override
  public String getSerializationId() {
    return "snapshooter";
  }
}
