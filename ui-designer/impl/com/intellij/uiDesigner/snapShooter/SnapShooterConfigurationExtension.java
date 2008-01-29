/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner.snapShooter;

import com.intellij.execution.JavaRunConfigurationExtension;
import com.intellij.execution.application.ApplicationConfiguration;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.ide.ui.LafManagerListener;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.components.BaseComponent;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.extensions.AreaInstance;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.pom.Navigatable;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.lw.LwComponent;
import com.intellij.util.PathUtil;
import com.intellij.util.net.NetUtils;
import com.intellij.xml.util.XmlStringUtil;
import com.jgoodies.forms.layout.FormLayout;
import gnu.trove.THashMap;
import org.jdom.Document;

import java.io.IOException;
import java.util.Set;
import java.util.TreeSet;

/**
 * @author yole
 */
public class SnapShooterConfigurationExtension implements JavaRunConfigurationExtension {
  public void updateJavaParameters(final RunConfiguration configuration, final JavaParameters params) {
    if (!(configuration instanceof ApplicationConfiguration)) {
      return;
    }
    ApplicationConfiguration appConfiguration = (ApplicationConfiguration) configuration;
    SnapShooterConfigurationSettings settings =
      (SnapShooterConfigurationSettings)appConfiguration.getExtensionSettings(SnapShooterConfigurationExtension.class);
    if (settings == null) {
      settings = new SnapShooterConfigurationSettings();
      appConfiguration.setExtensionSettings(SnapShooterConfigurationExtension.class, settings);
    }
    if (appConfiguration.ENABLE_SWING_INSPECTOR) {
      try {
        settings.setLastPort(NetUtils.findAvailableSocketPort());
      }
      catch(IOException ex) {
        settings.setLastPort(-1);
      }
    }

    if (appConfiguration.ENABLE_SWING_INSPECTOR && settings.getLastPort() != -1) {
      params.getProgramParametersList().prepend(appConfiguration.MAIN_CLASS_NAME);
      params.getProgramParametersList().prepend(Integer.toString(settings.getLastPort()));
      // add +1 because idea_rt.jar will be added as the last entry to the classpath
      params.getProgramParametersList().prepend(Integer.toString(params.getClassPath().getPathList().size() + 1));
      Set<String> paths = new TreeSet<String>();
      paths.add(PathUtil.getJarPathForClass(SnapShooter.class));         // ui-designer-impl
      paths.add(PathUtil.getJarPathForClass(BaseComponent.class));       // appcore-api
      paths.add(PathUtil.getJarPathForClass(ProjectComponent.class));    // openapi
      paths.add(PathUtil.getJarPathForClass(LwComponent.class));         // UIDesignerCore
      paths.add(PathUtil.getJarPathForClass(GridConstraints.class));     // forms_rt
      paths.add(PathUtil.getJarPathForClass(JDOMExternalizable.class));  // util
      paths.add(PathUtil.getJarPathForClass(Document.class));            // JDOM
      paths.add(PathUtil.getJarPathForClass(LafManagerListener.class));  // ui-impl
      paths.add(PathUtil.getJarPathForClass(DataProvider.class));        // action-system-openapi
      paths.add(PathUtil.getJarPathForClass(XmlStringUtil.class));       // idea
      paths.add(PathUtil.getJarPathForClass(Navigatable.class));         // pom
      paths.add(PathUtil.getJarPathForClass(AreaInstance.class));        // extensions
      paths.add(PathUtil.getJarPathForClass(THashMap.class));            // trove4j
      paths.add(PathUtil.getJarPathForClass(FormLayout.class));          // jgoodies
      for(String path: paths) {
        params.getClassPath().addFirst(path);
      }
      params.setMainClass("com.intellij.uiDesigner.snapShooter.SnapShooter");
    }
  }

  public void handleStartProcess(final RunConfiguration configuration, final OSProcessHandler handler) {
    SnapShooterConfigurationSettings settings =
      (SnapShooterConfigurationSettings)configuration.getExtensionSettings(SnapShooterConfigurationExtension.class);
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
}