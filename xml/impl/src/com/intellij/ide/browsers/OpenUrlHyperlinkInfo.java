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
import com.intellij.ide.BrowserUtil;
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

import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseEvent;

/**
 * @author nik
 */
public class OpenUrlHyperlinkInfo implements HyperlinkWithPopupMenuInfo {
  private final String myUrl;
  private final Condition<BrowsersConfiguration.BrowserFamily> mySuitableBrowsers;

  public OpenUrlHyperlinkInfo(@NotNull String url) {
    this(url, Conditions.<BrowsersConfiguration.BrowserFamily>alwaysTrue());
  }

  public OpenUrlHyperlinkInfo(@NotNull String url, @NotNull Condition<BrowsersConfiguration.BrowserFamily> suitableBrowsers) {
    myUrl = url;
    mySuitableBrowsers = suitableBrowsers;
  }

  @Override
  public ActionGroup getPopupMenuGroup(@NotNull MouseEvent event) {
    final DefaultActionGroup group = new DefaultActionGroup();
    for (BrowsersConfiguration.BrowserFamily family : BrowsersConfiguration.getInstance().getActiveBrowsers()) {
      if (mySuitableBrowsers.value(family)) {
        group.add(new OpenUrlInBrowserAction(family));
      }
    }
    group.addAll(new CopyUrlToClipboardAction());

    return group;
  }

  @Override
  public void navigate(Project project) {
    BrowserUtil.launchBrowser(myUrl);
  }

  private class CopyUrlToClipboardAction extends AnAction {
    private CopyUrlToClipboardAction() {
      super("Copy URL", "Copy URL to clipboard", PlatformIcons.COPY_ICON);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      CopyPasteManager.getInstance().setContents(new StringSelection(myUrl));
    }
  }

  private class OpenUrlInBrowserAction extends AnAction {
    private final BrowsersConfiguration.BrowserFamily myFamily;

    public OpenUrlInBrowserAction(@NotNull BrowsersConfiguration.BrowserFamily family) {
      super("Open in " + family.getName(), "Open URL in " + family.getName(), family.getIcon());
      myFamily = family;
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      UrlOpener.launchBrowser(myFamily, myUrl);
    }
  }
}
