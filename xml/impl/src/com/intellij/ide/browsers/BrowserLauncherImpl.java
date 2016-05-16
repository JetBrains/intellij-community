/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.concurrency.JobScheduler;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.util.ExecUtil;
import com.intellij.ide.GeneralSettings;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.AppUIUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Url;
import com.intellij.util.Urls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.ide.BuiltInServerManager;

import java.net.URI;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public final class BrowserLauncherImpl extends BrowserLauncherAppless {
  @Override
  public void browse(@NotNull String url, @Nullable WebBrowser browser, @Nullable Project project) {
    BuiltInServerManager serverManager = BuiltInServerManager.getInstance();
    Url parsedUrl = Urls.parse(url, false);
    if (parsedUrl != null && serverManager.isOnBuiltInWebServer(parsedUrl)) {
      if (Registry.is("ide.built.in.web.server.activatable", false)) {
        PropertiesComponent.getInstance().setValue("ide.built.in.web.server.active", true);
      }

      url = serverManager.addAuthToken(parsedUrl).toExternalForm();
    }

    super.browse(url, browser, project);
  }

  @Override
  protected void browseUsingNotSystemDefaultBrowserPolicy(@NotNull URI uri, @NotNull GeneralSettings settings, @Nullable Project project) {
    WebBrowserManager browserManager = WebBrowserManager.getInstance();
    if (browserManager.getDefaultBrowserPolicy() == DefaultBrowserPolicy.FIRST || (SystemInfo.isMac && "open".equals(settings.getBrowserPath()))) {
      WebBrowser browser = browserManager.getFirstActiveBrowser();
      if (browser != null) {
        browseUsingPath(uri.toString(), null, browser, project, ArrayUtil.EMPTY_STRING_ARRAY);
        return;
      }
    }

    super.browseUsingNotSystemDefaultBrowserPolicy(uri, settings, project);
  }

  @Override
  protected void showError(@Nullable final String error,
                           @Nullable final WebBrowser browser,
                           @Nullable final Project project,
                           final String title,
                           @Nullable final Runnable launchTask) {
    AppUIUtil.invokeOnEdt(new Runnable() {
      @Override
      public void run() {
        if (Messages.showYesNoDialog(project, StringUtil.notNullize(error, "Unknown error"),
                                     title == null ? IdeBundle.message("browser.error") : title, Messages.OK_BUTTON,
                                     IdeBundle.message("button.fix"), null) == Messages.NO) {
          final BrowserSettings browserSettings = new BrowserSettings();
          if (ShowSettingsUtil.getInstance().editConfigurable(project, browserSettings, browser == null ? null : new Runnable() {
            @Override
            public void run() {
              browserSettings.selectBrowser(browser);
            }
          })) {
            if (launchTask != null) {
              launchTask.run();
            }
          }
        }
      }
    }, project == null ? null : project.getDisposed());
  }

  @Override
  protected void checkCreatedProcess(@Nullable final WebBrowser browser,
                                     @Nullable final Project project,
                                     @NotNull final GeneralCommandLine commandLine,
                                     @NotNull final Process process,
                                     @Nullable final Runnable launchTask) {
    if (isOpenCommandUsed(commandLine)) {
      final Future<?> future = ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
        @Override
        public void run() {
          try {
            if (process.waitFor() == 1) {
              showError(ExecUtil.readFirstLine(process.getErrorStream(), null), browser, project, null, launchTask);
            }
          }
          catch (InterruptedException ignored) {
          }
        }
      });
      // 10 seconds is enough to start
      JobScheduler.getScheduler().schedule(new Runnable() {
        @Override
        public void run() {
          future.cancel(true);
        }
      }, 10, TimeUnit.SECONDS);
    }
  }
}
