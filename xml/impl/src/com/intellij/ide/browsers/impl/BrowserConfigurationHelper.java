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
package com.intellij.ide.browsers.impl;

import com.intellij.ide.browsers.BrowsersConfiguration;
import com.intellij.openapi.util.io.WindowsRegistryUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class BrowserConfigurationHelper {
  private static final String START_MENU_KEY = "HKEY_LOCAL_MACHINE\\SOFTWARE\\Clients\\StartMenuInternet";

  /**
   * Read data from Windows registry (may take some time to run).
   */
  @NotNull
  public static Map<BrowsersConfiguration.BrowserFamily, String> getBrowserPathsFromRegistry() {
    Map<BrowsersConfiguration.BrowserFamily, String> map =
      new EnumMap<BrowsersConfiguration.BrowserFamily, String>(BrowsersConfiguration.BrowserFamily.class);

    List<String> sections = WindowsRegistryUtil.readRegistryBranch(START_MENU_KEY);
    for (String section : sections) {
      BrowsersConfiguration.BrowserFamily family = getFamily(section);
      if (family != null) {
        String pathToExe = WindowsRegistryUtil.readRegistryDefault(START_MENU_KEY + "\\" + section + "\\shell\\open\\command");
        if (pathToExe != null) {
          map.put(family, pathToExe);
        }
      }
    }

    return map;
  }

  @Nullable
  private static BrowsersConfiguration.BrowserFamily getFamily(String registryName) {
    registryName = registryName.toLowerCase();
    for (BrowsersConfiguration.BrowserFamily family : BrowsersConfiguration.BrowserFamily.values()) {
      if (registryName.contains(family.getName().toLowerCase(Locale.US))) {
        return family;
      }
    }

    return null;
  }
}
