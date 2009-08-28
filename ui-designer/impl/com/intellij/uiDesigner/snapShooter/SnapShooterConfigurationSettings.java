/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner.snapShooter;

import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.openapi.util.Key;

/**
 * @author yole
 */
public class SnapShooterConfigurationSettings {
  public static final Key<SnapShooterConfigurationSettings> SNAP_SHOOTER_KEY = Key.create("snap.shooter.settings");
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

  public static SnapShooterConfigurationSettings get(final RunConfigurationBase config) {
    SnapShooterConfigurationSettings settings = config.getUserData(SNAP_SHOOTER_KEY);
    if (settings == null) {
      settings = new SnapShooterConfigurationSettings();
      config.putUserData(SNAP_SHOOTER_KEY, settings);
    }
    return settings;
  }
}