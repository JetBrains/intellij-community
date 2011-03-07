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
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.xmlb.annotations.Tag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author nik
 */
public class ChromeSettings extends BrowserSpecificSettings {
  private String myUserDataDirectoryPath;
  private boolean myUseCustomProfile;

  public ChromeSettings() {
  }

  @Nullable
  @Tag("user-data-dir")
  public String getUserDataDirectoryPath() {
    return myUserDataDirectoryPath;
  }

  public void setUserDataDirectoryPath(String userDataDirectoryPath) {
    myUserDataDirectoryPath = userDataDirectoryPath;
  }

  @Tag("use-custom-profile")
  public boolean isUseCustomProfile() {
    return myUseCustomProfile;
  }

  public void setUseCustomProfile(boolean useCustomProfile) {
    myUseCustomProfile = useCustomProfile;
  }

  @NotNull
  @Override
  public String[] getAdditionalParameters() {
    if (!myUseCustomProfile || myUserDataDirectoryPath == null) {
      return ArrayUtil.EMPTY_STRING_ARRAY;
    }
    return new String[]{"--user-data-dir=" + FileUtil.toSystemDependentName(myUserDataDirectoryPath)};
  }

  @Override
  public Configurable createConfigurable() {
    return new ChromeSettingsConfigurable(this);
  }
}
