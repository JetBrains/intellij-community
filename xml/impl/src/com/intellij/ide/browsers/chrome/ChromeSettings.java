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

import com.intellij.execution.configurations.ParametersList;
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
  @NonNls public static final String USER_DATA_DIR_ARG = "--user-data-dir=";
  private String myCommandLineOptions = "";
  private String myUserDataDirectoryPath;
  private boolean myUseCustomProfile;

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

  @Tag("command-line-options")
  public String getCommandLineOptions() {
    return myCommandLineOptions;
  }

  public void setCommandLineOptions(String commandLineOptions) {
    myCommandLineOptions = commandLineOptions;
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
    String[] cliOptions = ParametersList.parse(myCommandLineOptions);
    if (myUseCustomProfile && myUserDataDirectoryPath != null) {
      return ArrayUtil.mergeArrays(cliOptions, USER_DATA_DIR_ARG + FileUtil.toSystemDependentName(myUserDataDirectoryPath));
    }
    else {
      return cliOptions;
    }
  }

  @Override
  public ChromeSettingsConfigurable createConfigurable() {
    return new ChromeSettingsConfigurable(this);
  }
}
