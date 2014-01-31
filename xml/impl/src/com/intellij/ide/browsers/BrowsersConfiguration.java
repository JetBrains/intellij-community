/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.ide.browsers;

import com.intellij.icons.AllIcons;
import com.intellij.ide.browsers.chrome.ChromeSettings;
import com.intellij.ide.browsers.firefox.FirefoxSettings;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.xml.XmlBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;

public class BrowsersConfiguration {
  public enum BrowserFamily implements Iconable {
    CHROME(XmlBundle.message("browsers.chrome"), "chrome", "google-chrome", "Google Chrome", AllIcons.Xml.Browsers.Chrome16) {
      @Override
      public BrowserSpecificSettings createBrowserSpecificSettings() {
        return new ChromeSettings();
      }
    },
    EXPLORER(XmlBundle.message("browsers.explorer"), "iexplore", null, null, AllIcons.Xml.Browsers.Explorer16),
    FIREFOX(XmlBundle.message("browsers.firefox"), "firefox", "firefox", "Firefox", AllIcons.Xml.Browsers.Firefox16) {
      @Override
      public BrowserSpecificSettings createBrowserSpecificSettings() {
        return new FirefoxSettings();
      }
    },
    OPERA(XmlBundle.message("browsers.opera"), "opera", "opera", "Opera", AllIcons.Xml.Browsers.Opera16),
    SAFARI(XmlBundle.message("browsers.safari"), "safari", null, "Safari", AllIcons.Xml.Browsers.Safari16);

    private final String myName;
    private final String myWindowsPath;
    private final String myUnixPath;
    private final String myMacPath;
    private final Icon myIcon;

    BrowserFamily(@NotNull String name,
                  @NotNull final String windowsPath,
                  @Nullable final String unixPath,
                  @Nullable final String macPath,
                  @NotNull Icon icon) {
      myName = name;
      myWindowsPath = windowsPath;
      myUnixPath = unixPath;
      myMacPath = macPath;
      myIcon = icon;
    }

    @Nullable
    public BrowserSpecificSettings createBrowserSpecificSettings() {
      return null;
    }

    @Nullable
    public String getExecutionPath() {
      if (SystemInfo.isWindows) {
        return myWindowsPath;
      }
      else if (SystemInfo.isMac) {
        return myMacPath;
      }
      else {
        return myUnixPath;
      }
    }

    public String getName() {
      return myName;
    }

    public Icon getIcon() {
      return myIcon;
    }

    @Override
    public String toString() {
      return myName;
    }

    @Override
    public Icon getIcon(@IconFlags int flags) {
      return getIcon();
    }
  }

  @SuppressWarnings({"UnusedDeclaration"})
  @Nullable
  @Deprecated
  /**
   * @deprecated  to remove in IDEA 14
   */
  public static BrowsersConfiguration getInstance() {
    return ServiceManager.getService(BrowsersConfiguration.class);
  }

  @SuppressWarnings({"UnusedDeclaration"})
  @Nullable
  @Deprecated
  /**
   * @deprecated  to remove in IDEA 14
   */
  public BrowserFamily findFamilyByName(@Nullable String name) {
    for (BrowserFamily family : BrowserFamily.values()) {
      if (family.getName().equals(name)) {
        return family;
      }
    }
    return null;
  }

  @SuppressWarnings({"UnusedDeclaration"})
  @Nullable
  @Deprecated
  /**
   * @deprecated  to remove in IDEA 14
   */
  public BrowserFamily findFamilyByPath(@Nullable String path) {
    if (!StringUtil.isEmptyOrSpaces(path)) {
      String name = FileUtil.getNameWithoutExtension(new File(path).getName());
      for (BrowserFamily family : BrowserFamily.values()) {
        if (name.equalsIgnoreCase(family.getExecutionPath())) {
          return family;
        }
      }
    }

    return null;
  }
}