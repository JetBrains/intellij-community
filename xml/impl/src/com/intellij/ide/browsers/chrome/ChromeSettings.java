/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.ide.browsers.chrome;

import com.intellij.ide.browsers.BrowserSpecificSettings;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.xmlb.annotations.Tag;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author nik
 */
public class ChromeSettings extends BrowserSpecificSettings {
  @NonNls public static final String REMOTE_SHELL_PORT_ARG = "--remote-shell-port=";
  @NonNls public static final String USER_DATA_DIR_ARG = "--user-data-dir=";
  public static final int DEFAULT_REMOTE_SHELL_PORT = 7930;
  private String myUserDataDirectoryPath;
  private boolean myUseCustomProfile;
  private boolean myEnableRemoteDebug;
  private int myRemoteShellPort = DEFAULT_REMOTE_SHELL_PORT;

  public ChromeSettings() {
  }

  @Nullable
  @Tag("user-data-dir")
  public String getUserDataDirectoryPath() {
    return myUserDataDirectoryPath;
  }

  @Tag("use-custom-profile")
  public boolean isUseCustomProfile() {
    return myUseCustomProfile;
  }

  @Tag("enable-remote-debug")
  public boolean isEnableRemoteDebug() {
    return myEnableRemoteDebug;
  }

  @Tag("remote-shell-port")
  public int getRemoteShellPort() {
    return myRemoteShellPort;
  }

  public void setEnableRemoteDebug(boolean enableRemoteDebug) {
    myEnableRemoteDebug = enableRemoteDebug;
  }

  public void setRemoteShellPort(int remoteShellPort) {
    myRemoteShellPort = remoteShellPort;
  }

  public void setUserDataDirectoryPath(String userDataDirectoryPath) {
    myUserDataDirectoryPath = userDataDirectoryPath;
  }

  public void setUseCustomProfile(boolean useCustomProfile) {
    myUseCustomProfile = useCustomProfile;
  }

  @NotNull
  @Override
  public String[] getAdditionalParameters() {
    String[] customProfileArg;
    if (myUseCustomProfile && myUserDataDirectoryPath != null) {
      customProfileArg = new String[]{USER_DATA_DIR_ARG + FileUtil.toSystemDependentName(myUserDataDirectoryPath)};
    }
    else {
      customProfileArg = ArrayUtil.EMPTY_STRING_ARRAY;
    }

    String[] remoteShellArg;
    if (myEnableRemoteDebug) {
      remoteShellArg = new String[]{REMOTE_SHELL_PORT_ARG + myRemoteShellPort};
    }
    else {
      remoteShellArg = ArrayUtil.EMPTY_STRING_ARRAY;
    }

    return ArrayUtil.mergeArrays(customProfileArg, remoteShellArg);
  }

  @Override
  public ChromeSettingsConfigurable createConfigurable() {
    return new ChromeSettingsConfigurable(this);
  }
}
