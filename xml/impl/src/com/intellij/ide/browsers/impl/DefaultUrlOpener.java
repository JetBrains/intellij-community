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

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.util.ExecUtil;
import com.intellij.ide.BrowserUtil;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.browsers.BrowserSpecificSettings;
import com.intellij.ide.browsers.UrlOpener;
import com.intellij.ide.browsers.WebBrowser;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.xml.XmlBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class DefaultUrlOpener extends UrlOpener {
  private static final Logger LOG = Logger.getInstance(DefaultUrlOpener.class);

  @Override
  public boolean openUrl(@NotNull WebBrowser browser, @NotNull String url) {
    return launchBrowser(browser, url, false);
  }

  public static boolean launchBrowser(@NotNull WebBrowser browser,
                                      @Nullable String url,
                                      boolean newWindowIfPossible,
                                      @NotNull String... additionalParameters) {
    final String browserPath = browser.getPath();
    if (StringUtil.isEmpty(browserPath)) {
      Messages.showErrorDialog(browser.getBrowserNotFoundMessage(), IdeBundle.message("title.browser.not.found"));
      return false;
    }

    return doLaunchBrowser(browserPath, browser.getSpecificSettings(), url, newWindowIfPossible, additionalParameters);
  }

  private static boolean doLaunchBrowser(final String browserPath,
                                         @Nullable BrowserSpecificSettings browserSpecificSettings,
                                         final String url,
                                         final boolean newWindowIfPossible,
                                         final String[] additionalParameters) {
    List<String> command = BrowserUtil.getOpenBrowserCommand(browserPath, newWindowIfPossible);
    if (url != null) {
      command.add(url);
    }
    addArgs(command, browserSpecificSettings, additionalParameters);

    try {
      new GeneralCommandLine(command).createProcess();
      return true;
    }
    catch (ExecutionException e) {
      Messages.showErrorDialog(e.getMessage(), XmlBundle.message("browser.error"));
      return false;
    }
  }

  private static void addArgs(List<String> command, @Nullable BrowserSpecificSettings settings, String[] additional) {
    List<String> specific = settings == null ? Collections.<String>emptyList() : settings.getAdditionalParameters();
    if (specific.size() + additional.length > 0) {
      if (SystemInfo.isMac && ExecUtil.getOpenCommandPath().equals(command.get(0))) {
        if (BrowserUtil.isOpenCommandSupportArgs()) {
          command.add("--args");
        }
        else {
          LOG.warn("'open' command doesn't allow to pass command line arguments so they will be ignored: " +
                   StringUtil.join(specific, ", ") + " " + Arrays.toString(additional));
        }
      }

      command.addAll(specific);
      Collections.addAll(command, additional);
    }
  }
}
