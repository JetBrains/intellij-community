/*
 * Copyright 2000-2009 JetBrains s.r.o.
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