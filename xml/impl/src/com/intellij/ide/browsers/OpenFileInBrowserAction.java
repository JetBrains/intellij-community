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

import com.intellij.ide.BrowserUtil;
import com.intellij.ide.GeneralSettings;
import com.intellij.ide.browsers.impl.WebBrowserServiceImpl;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.xml.XmlBundle;
import com.intellij.xml.util.HtmlUtil;

import java.awt.event.InputEvent;

public class OpenFileInBrowserAction extends DumbAwareAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.browsers.OpenFileInBrowserAction");

  @Override
  public void update(AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    final PsiFile file = LangDataKeys.PSI_FILE.getData(dataContext);
    final Presentation presentation = e.getPresentation();

    if (file == null || file.getVirtualFile() == null) {
      presentation.setVisible(false);
      presentation.setEnabled(false);
      return;
    }

    Pair<WebBrowserUrlProvider, Url> browserUrlProvider = WebBrowserServiceImpl.getProvider(file);
    final boolean isHtmlFile = HtmlUtil.isHtmlFile(file);
    if (browserUrlProvider == null) {
      if (file.getVirtualFile() instanceof LightVirtualFile) {
        presentation.setVisible(false);
        presentation.setEnabled(false);
        return;
      }
      else {
        presentation.setEnabled(isHtmlFile);
      }
    }
    else {
      presentation.setEnabled(true);
    }
    presentation.setVisible(true);

    String text = getTemplatePresentation().getText();
    String description = getTemplatePresentation().getDescription();

    if (browserUrlProvider != null) {
      final String customText = browserUrlProvider.first.getOpenInBrowserActionText(file);
      if (customText != null) {
        text = customText;
      }
      final String customDescription = browserUrlProvider.first.getOpenInBrowserActionDescription(file);
      if (customDescription != null) {
        description = customDescription;
      }
      if (isHtmlFile) {
        description += " (hold Shift to open URL of local file)";
      }
    }

    presentation.setText(text);
    presentation.setDescription(description);

    GeneralSettings settings = GeneralSettings.getInstance();
    if (!settings.isUseDefaultBrowser()) {
      BrowsersConfiguration.BrowserFamily family = BrowsersConfiguration.getInstance().findFamilyByPath(settings.getBrowserPath());
      if (family != null) {
        presentation.setIcon(family.getIcon());
      }
    }

    if (ActionPlaces.isPopupPlace(e.getPlace())) {
      presentation.setVisible(presentation.isEnabled());
    }
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    final PsiFile psiFile = LangDataKeys.PSI_FILE.getData(dataContext);
    LOG.assertTrue(psiFile != null);
    try {
      final InputEvent event = e.getInputEvent();
      Url url = WebBrowserService.getInstance().getUrlToOpen(psiFile, event != null && event.isShiftDown());
      if (url != null) {
        ApplicationManager.getApplication().saveAll();
        BrowserUtil.launchBrowser(url.toExternalForm());
      }
    }
    catch (WebBrowserUrlProvider.BrowserException e1) {
      Messages.showErrorDialog(e1.getMessage(), XmlBundle.message("browser.error"));
    }
    catch (Exception e1) {
      LOG.error(e1);
    }
  }
}
