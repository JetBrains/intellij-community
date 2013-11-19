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

import com.intellij.execution.filters.HyperlinkWithPopupMenuInfo;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Conditions;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseEvent;

public final class OpenUrlHyperlinkInfo implements HyperlinkWithPopupMenuInfo {
  private final String url;
  private final WebBrowser browser;
  private final Condition<WebBrowser> browserCondition;

  public OpenUrlHyperlinkInfo(@NotNull String url) {
    this(url, Conditions.<WebBrowser>alwaysTrue(), null);
  }

  public OpenUrlHyperlinkInfo(@NotNull String url, @NotNull WebBrowser browser) {
    this(url, null, browser);
  }

  public OpenUrlHyperlinkInfo(@NotNull String url, @NotNull Condition<WebBrowser> browserCondition) {
    this(url, browserCondition, null);
  }

  private OpenUrlHyperlinkInfo(@NotNull String url, @Nullable Condition<WebBrowser> browserCondition, @Nullable WebBrowser browser) {
    this.url = url;
    this.browserCondition = browserCondition;
    this.browser = browser;
  }

  @Override
  public ActionGroup getPopupMenuGroup(@NotNull MouseEvent event) {
    DefaultActionGroup group = new DefaultActionGroup();
    for (BrowsersConfiguration.BrowserFamily browserFamily : BrowsersConfiguration.getInstance().getActiveBrowsers()) {
      final WebBrowser browser = WebBrowser.getStandardBrowser(browserFamily);
      if (browserCondition == null ? browser.equals(this.browser) : browserCondition.value(browser)) {
        group.add(new AnAction("Open in " + browser.getName(), "Open URL in " + browser.getName(), browser.getIcon()) {
          @Override
          public void actionPerformed(AnActionEvent e) {
            UrlOpener.launchBrowser(url, browser);
          }
        });
      }
    }

    group.add(new AnAction("Copy URL", "Copy URL to clipboard", PlatformIcons.COPY_ICON) {
      @Override
      public void actionPerformed(AnActionEvent e) {
        CopyPasteManager.getInstance().setContents(new StringSelection(url));
      }
    });
    return group;
  }

  @Override
  public void navigate(Project project) {
    UrlOpener.launchBrowser(url, browser);
  }
}
