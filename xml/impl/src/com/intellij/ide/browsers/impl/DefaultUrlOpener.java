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

import com.intellij.concurrency.JobScheduler;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.util.ExecUtil;
import com.intellij.ide.BrowserUtil;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.browsers.BrowserSettings;
import com.intellij.ide.browsers.BrowserSpecificSettings;
import com.intellij.ide.browsers.UrlOpener;
import com.intellij.ide.browsers.WebBrowser;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.AppUIUtil;
import com.intellij.xml.XmlBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class DefaultUrlOpener extends UrlOpener {
  private static final Logger LOG = Logger.getInstance(DefaultUrlOpener.class);

  @Override
  public boolean openUrl(@NotNull WebBrowser browser, @NotNull String url, @Nullable Project project) {
    return launchBrowser(browser, url, false, project);
  }

  public static boolean launchBrowser(@NotNull final WebBrowser browser,
                                      @Nullable String url,
                                      boolean newWindowIfPossible,
                                      @Nullable final Project project,
                                      @NotNull String... additionalParameters) {
    final String browserPath = browser.getPath();
    if (StringUtil.isEmpty(browserPath)) {
      AppUIUtil.invokeOnEdt(new Runnable() {
        @Override
        public void run() {
          Messages.showErrorDialog(project, browser.getBrowserNotFoundMessage(), IdeBundle.message("title.browser.not.found"));
        }
      });
      return false;
    }

    return doLaunchBrowser(browserPath, browser.getSpecificSettings(), url, newWindowIfPossible, project, browser, additionalParameters);
  }

  private static boolean doLaunchBrowser(final String browserPath,
                                         @Nullable BrowserSpecificSettings browserSpecificSettings,
                                         final String url,
                                         final boolean newWindowIfPossible,
                                         @Nullable final Project project,
                                         @NotNull final WebBrowser browser,
                                         final String[] additionalParameters) {
    GeneralCommandLine commandLine = new GeneralCommandLine(BrowserUtil.getOpenBrowserCommand(browserPath, newWindowIfPossible));
    if (url != null) {
      commandLine.addParameter(url);
    }

    addArgs(commandLine, browserSpecificSettings, additionalParameters);

    try {
      final Process process = commandLine.createProcess();
      if (isOpenCommandUsed(commandLine)) {
        final Future<?> future = ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
          @Override
          public void run() {
            try {
              if (process.waitFor() == 1) {
                showError(ExecUtil.readFirstLine(process.getErrorStream(), null), browser, project);
              }
            }
            catch (InterruptedException ignored) {
            }
          }
        });
        // 30 seconds is enough to start
        JobScheduler.getScheduler().schedule(new Runnable() {
          @Override
          public void run() {
            future.cancel(true);
          }
        }, 30, TimeUnit.MILLISECONDS);
      }
      return true;
    }
    catch (ExecutionException e) {
      showError(e.getMessage(), browser, project);
      return false;
    }
  }

  private static void showError(@Nullable final String error, @Nullable final WebBrowser browser, @Nullable final Project project) {
    AppUIUtil.invokeOnEdt(new Runnable() {
      @Override
      public void run() {
        if (Messages.showYesNoDialog(StringUtil.notNullize(error, "Unknown error"), XmlBundle.message("browser.error"), Messages.OK_BUTTON, "Fix\u2026", null) == Messages.NO) {
          final BrowserSettings browserSettings = new BrowserSettings();
          ShowSettingsUtil.getInstance().editConfigurable(project, browserSettings, browser == null ? null : new Runnable() {
            @Override
            public void run() {
              browserSettings.selectBrowser(browser);
            }
          });
        }
      }
    });
  }

  private static void addArgs(@NotNull GeneralCommandLine command, @Nullable BrowserSpecificSettings settings, @NotNull String[] additional) {
    List<String> specific = settings == null ? Collections.<String>emptyList() : settings.getAdditionalParameters();
    if (specific.size() + additional.length > 0) {
      if (isOpenCommandUsed(command)) {
        if (BrowserUtil.isOpenCommandSupportArgs()) {
          command.addParameter("--args");
        }
        else {
          LOG.warn("'open' command doesn't allow to pass command line arguments so they will be ignored: " +
                   StringUtil.join(specific, ", ") + " " + Arrays.toString(additional));
          return;
        }
      }

      command.addParameters(specific);
      command.addParameters(additional);
    }
  }

  private static boolean isOpenCommandUsed(@NotNull GeneralCommandLine command) {
    return SystemInfo.isMac && ExecUtil.getOpenCommandPath().equals(command.getExePath());
  }
}
