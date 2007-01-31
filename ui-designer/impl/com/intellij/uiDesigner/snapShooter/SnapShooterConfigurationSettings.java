/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner.snapShooter;

import com.intellij.execution.configurations.RunConfiguration;

/**
 * @author yole
 */
public class SnapShooterConfigurationSettings {
  private int myLastPort;
  private Runnable myNotifyRunnable;

  public int getLastPort() {
    return myLastPort;
  }

  public Runnable getNotifyRunnable() {
    return myNotifyRunnable;
  }

  public void setLastPort(final int lastPort) {
    myLastPort = lastPort;
  }

  public void setNotifyRunnable(final Runnable notifyRunnable) {
    myNotifyRunnable = notifyRunnable;
  }

  public static SnapShooterConfigurationSettings get(final RunConfiguration config) {
    SnapShooterConfigurationSettings settings =
      (SnapShooterConfigurationSettings) config.getExtensionSettings(SnapShooterConfigurationExtension.class);
    if (settings == null) {
      settings = new SnapShooterConfigurationSettings();
      config.setExtensionSettings(SnapShooterConfigurationExtension.class, settings);
    }
    return settings;
  }
}