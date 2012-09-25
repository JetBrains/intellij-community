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
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xml.XmlBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class UrlOpenerImpl extends UrlOpener {
  private static final Logger LOG = Logger.getInstance(UrlOpenerImpl.class);

  @Override
  public boolean openUrl(BrowsersConfiguration.BrowserFamily family, String url) {
    doLaunchBrowser(family, url, ArrayUtil.EMPTY_STRING_ARRAY, Conditions.<String>alwaysTrue(), false);
    return true;
  }

  public static void doLaunchBrowser(final BrowsersConfiguration.BrowserFamily family,
                               @NotNull String url,
                               @NotNull String[] additionalParameters,
                               @NotNull Condition<String> browserSpecificParametersFilter,
                               final boolean forceOpenNewInstanceOnMac) {
    final WebBrowserSettings settings = BrowsersConfiguration.getInstance().getBrowserSettings(family);
    final String path = settings.getPath();
    String pathCheckResult = BrowsersConfiguration.checkPath(family, path);
    if (pathCheckResult == null) {
      url = BrowserUtil.escapeUrl(url);
      try {
        final BrowserSpecificSettings specificSettings = settings.getBrowserSpecificSettings();
        final String[] browserParameters = specificSettings != null ? specificSettings.getAdditionalParameters() : ArrayUtil.EMPTY_STRING_ARRAY;
        String[] parameters = ArrayUtil.mergeArrays(ContainerUtil.findAllAsArray(browserParameters, browserSpecificParametersFilter),
                                                    additionalParameters);
        launchBrowser(path, url, forceOpenNewInstanceOnMac, parameters);
      }
      catch (IOException e) {
        Messages.showErrorDialog(e.getMessage(), XmlBundle.message("browser.error"));
      }
    }
    else {
      Messages.showErrorDialog(pathCheckResult, XmlBundle.message("browser.path.not.specified.title"));
    }
  }

  public static void launchBrowser(@NonNls @NotNull String browserPath,
                                   String url, final boolean forceOpenNewInstanceOnMac,
                                   @NonNls String... browserArgs) throws IOException {
    final List<String> command = BrowserUtil.getOpenBrowserCommand(browserPath);
    String[] args = ArrayUtil.append(browserArgs, url);
    if (SystemInfo.isMac && ExecUtil.getOpenCommandPath().equals(command.get(0))) {
      if (browserArgs.length > 0) {
        if (BrowserUtil.isOpenCommandSupportArgs()) {
          args = ArrayUtil.mergeArrays(new String[]{url, "--args"}, browserArgs);
        }
        else {
          args = new String[]{url};
          LOG.warn("'open' command doesn't allow to pass command line arguments so they will be ignored: " + Arrays.toString(args));
        }
      }
      if (forceOpenNewInstanceOnMac) {
        args = ArrayUtil.mergeArrays(new String[]{"-n"}, args);
      }
    }

    Collections.addAll(command, args);
    if (LOG.isDebugEnabled()) {
      LOG.debug("Launching browser: " + Arrays.toString(command.toArray()));
    }
    new ProcessBuilder(command).start();
  }
}
