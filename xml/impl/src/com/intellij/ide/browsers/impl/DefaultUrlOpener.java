/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.execution.util.ExecUtil;
import com.intellij.ide.BrowserUtil;
import com.intellij.ide.browsers.BrowserSpecificSettings;
import com.intellij.ide.browsers.BrowsersConfiguration;
import com.intellij.ide.browsers.UrlOpener;
import com.intellij.ide.browsers.WebBrowserSettings;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Conditions;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xml.XmlBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DefaultUrlOpener extends UrlOpener {
  private static final Logger LOG = Logger.getInstance(DefaultUrlOpener.class);

  @Override
  public boolean openUrl(BrowsersConfiguration.BrowserFamily family, @Nullable String url) {
    return launchBrowser(family, url, ArrayUtil.EMPTY_STRING_ARRAY, Conditions.<String>alwaysTrue(), false);
  }

  public static boolean launchBrowser(final BrowsersConfiguration.BrowserFamily family,
                                      @Nullable String url,
                                      @NotNull String[] additionalParameters,
                                      @NotNull Condition<String> browserSpecificParametersFilter,
                                      final boolean forceOpenNewInstanceOnMac) {
    final WebBrowserSettings settings = BrowsersConfiguration.getInstance().getBrowserSettings(family);
    final String path = settings.getPath();
    if (StringUtil.isEmpty(path)) {
      Messages.showErrorDialog(XmlBundle.message("browser.path.not.specified", family.getName()), XmlBundle.message("browser.path.not.specified.title"));
      return false;
    }

    try {
      BrowserSpecificSettings specificSettings = settings.getBrowserSpecificSettings();
      List<String> parameters = specificSettings == null
                                ? (additionalParameters.length == 0 ? Collections.<String>emptyList() : new ArrayList<String>())
                                : ContainerUtil.findAll(specificSettings.getAdditionalParameters(), browserSpecificParametersFilter);
      Collections.addAll(parameters, additionalParameters);
      doLaunchBrowser(path, url == null ? null : BrowserUtil.escapeUrl(url), forceOpenNewInstanceOnMac, parameters);
      return true;
    }
    catch (IOException e) {
      Messages.showErrorDialog(e.getMessage(), XmlBundle.message("browser.error"));
      return false;
    }
  }

  private static void doLaunchBrowser(String browserPath, @Nullable String url, boolean forceOpenNewInstanceOnMac, List<String> browserArgs)
    throws IOException {
    List<String> command = BrowserUtil.getOpenBrowserCommand(browserPath);
    addArgs(command, browserArgs, url, forceOpenNewInstanceOnMac);
    if (LOG.isDebugEnabled()) {
      LOG.debug("Launching browser: " + StringUtil.join(command, " "));
    }
    new ProcessBuilder(command).start();
  }

  private static void addArgs(List<String> command, List<String> browserArgs, @Nullable String url, boolean forceOpenNewInstanceOnMac) {
    if (SystemInfo.isMac && ExecUtil.getOpenCommandPath().equals(command.get(0))) {
      if (forceOpenNewInstanceOnMac) {
        command.add("-n");
      }
      if (url != null) {
        command.add(url);
      }

      if (!browserArgs.isEmpty()) {
        if (BrowserUtil.isOpenCommandSupportArgs()) {
          command.add("--args");
          command.addAll(browserArgs);
        }
        else {
          LOG.warn(
            "'open' command doesn't allow to pass command line arguments so they will be ignored: " + StringUtil.join(browserArgs, " "));
        }
      }
    }
    else {
      if (url != null) {
        command.add(url);
      }
      command.addAll(browserArgs);
    }
  }
}
