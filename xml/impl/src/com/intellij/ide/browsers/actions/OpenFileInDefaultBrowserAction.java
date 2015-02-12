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
package com.intellij.ide.browsers.actions;

import com.intellij.ide.GeneralSettings;
import com.intellij.ide.browsers.*;
import com.intellij.internal.statistic.UsageTrigger;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.xml.util.HtmlUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class OpenFileInDefaultBrowserAction extends DumbAwareAction {
  @Override
  public void update(@NotNull AnActionEvent e) {
    Presentation presentation = e.getPresentation();

    OpenInBrowserRequest result = BaseOpenInBrowserAction.doUpdate(e);
    if (result == null) {
      return;
    }

    String description = getTemplatePresentation().getDescription();
    if (HtmlUtil.isHtmlFile(result.getFile())) {
      description += " (hold Shift to open URL of local file)";
    }

    presentation.setText(getTemplatePresentation().getText());
    presentation.setDescription(description);

    WebBrowser browser = findUsingBrowser();
    if (browser != null) {
      presentation.setIcon(browser.getIcon());
    }

    if (ActionPlaces.isPopupPlace(e.getPlace())) {
      presentation.setVisible(presentation.isEnabled());
    }
  }

  @Nullable
  public static WebBrowser findUsingBrowser() {
    WebBrowserManager browserManager = WebBrowserManager.getInstance();
    DefaultBrowserPolicy defaultBrowserPolicy = browserManager.getDefaultBrowserPolicy();
    if (defaultBrowserPolicy == DefaultBrowserPolicy.FIRST || (defaultBrowserPolicy == DefaultBrowserPolicy.SYSTEM && !BrowserLauncherAppless.canUseSystemDefaultBrowserPolicy())) {
      return browserManager.getFirstActiveBrowser();
    }
    else if (defaultBrowserPolicy == DefaultBrowserPolicy.ALTERNATIVE) {
      String path = GeneralSettings.getInstance().getBrowserPath();
      if (!StringUtil.isEmpty(path)) {
        WebBrowser browser = browserManager.findBrowserById(path);
        if (browser == null) {
          for (WebBrowser item : browserManager.getActiveBrowsers()) {
            if (path.equals(item.getPath())) {
              return item;
            }
          }
        }
      }
    }
    return null;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    UsageTrigger.trigger("OpenInBrowser.default");
    BaseOpenInBrowserAction.open(e, findUsingBrowser());
  }
}
