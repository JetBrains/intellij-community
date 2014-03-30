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
import com.intellij.ide.browsers.OpenInBrowserRequest;
import com.intellij.ide.browsers.WebBrowser;
import com.intellij.ide.browsers.WebBrowserManager;
import com.intellij.ide.browsers.WebBrowserUrlProvider;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.xml.util.HtmlUtil;
import org.jetbrains.annotations.Nullable;

public class OpenFileInDefaultBrowserAction extends DumbAwareAction {
  @Override
  public void update(AnActionEvent e) {
    Presentation presentation = e.getPresentation();

    Pair<OpenInBrowserRequest, WebBrowserUrlProvider> result = BaseOpenInBrowserAction.doUpdate(e);
    if (result == null) {
      return;
    }

    WebBrowserUrlProvider browserUrlProvider = result.second;
    String text = getTemplatePresentation().getText();
    String description = getTemplatePresentation().getDescription();
    if (browserUrlProvider != null) {
      String customDescription = browserUrlProvider.getOpenInBrowserActionDescription(result.first.getFile());
      if (customDescription != null) {
        description = customDescription;
      }
      if (HtmlUtil.isHtmlFile(result.first.getFile())) {
        description += " (hold Shift to open URL of local file)";
      }
    }

    presentation.setText(text);
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
  private static WebBrowser findUsingBrowser() {
    WebBrowserManager browserManager = WebBrowserManager.getInstance();
    if (browserManager.getDefaultBrowserMode() == WebBrowserManager.DefaultBrowser.FIRST) {
      return browserManager.getDefaultBrowser();
    }
    else if (browserManager.getDefaultBrowserMode() == WebBrowserManager.DefaultBrowser.ALTERNATIVE) {
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
  public void actionPerformed(AnActionEvent e) {
    BaseOpenInBrowserAction.open(e, findUsingBrowser());
  }
}
