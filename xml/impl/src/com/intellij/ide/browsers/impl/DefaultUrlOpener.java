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
import com.intellij.ide.browsers.BrowserSpecificSettings;
import com.intellij.ide.browsers.BrowsersConfiguration;
import com.intellij.ide.browsers.UrlOpener;
import com.intellij.ide.browsers.WebBrowserSettings;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.xml.XmlBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class DefaultUrlOpener extends UrlOpener {
  private static final Logger LOG = Logger.getInstance(DefaultUrlOpener.class);

  @Override
  public boolean openUrl(@NotNull BrowsersConfiguration.BrowserFamily family, @Nullable String url) {
    return launchBrowser(family, url, false);
  }

  /** @deprecated use {@linkplain #launchBrowser(BrowsersConfiguration.BrowserFamily, String, boolean, String...)} (to remove in IDEA 13) */
  @SuppressWarnings("unused")
  public static boolean launchBrowser(@NotNull BrowsersConfiguration.BrowserFamily family,
                                      @Nullable String url,
                                      @NotNull String[] additionalParameters,
                                      @NotNull Condition<String> browserSpecificParametersFilter,
                                      boolean newWindowIfPossible) {
    return launchBrowser(family, url, newWindowIfPossible, additionalParameters);
  }

  public static boolean launchBrowser(@NotNull BrowsersConfiguration.BrowserFamily family,
                                      @Nullable String url,
                                      boolean newWindowIfPossible,
                                      @NotNull String... additionalParameters) {
    WebBrowserSettings settings = BrowsersConfiguration.getInstance().getBrowserSettings(family);
    String path = settings.getPath();
    if (StringUtil.isEmpty(path)) {
      String message = XmlBundle.message("browser.path.not.specified", family.getName());
      Messages.showErrorDialog(message, XmlBundle.message("browser.path.not.specified.title"));
      return false;
    }

    List<String> command = BrowserUtil.getOpenBrowserCommand(path, newWindowIfPossible);
    if (url != null) {
      command.add(url);
    }
    addArgs(command, settings.getBrowserSpecificSettings(), additionalParameters);

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
    String[] specific = settings != null ? settings.getAdditionalParameters() : ArrayUtil.EMPTY_STRING_ARRAY;

    if (specific.length + additional.length > 0) {
      if (SystemInfo.isMac && ExecUtil.getOpenCommandPath().equals(command.get(0))) {
        if (!BrowserUtil.isOpenCommandSupportArgs()) {
          LOG.warn("'open' command doesn't allow to pass command line arguments so they will be ignored: " +
                   Arrays.toString(specific) + " " + Arrays.toString(additional));
        }
        else {
          command.add("--args");
        }
      }

      Collections.addAll(command, specific);
      Collections.addAll(command, additional);
    }
  }
}
