package com.intellij.ide.browsers;

import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiFile;
import com.intellij.xml.XmlBundle;

public class OpenFileInBrowserAction extends DumbAwareAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.browsers.OpenFileInBrowserAction");

  public void update(AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    final PsiFile file = LangDataKeys.PSI_FILE.getData(dataContext);
    final Presentation presentation = e.getPresentation();

    if (file != null) {
      presentation.setVisible(true);

      final WebBrowserUrlProvider browserUrlProvider = WebBrowserUrlProviders.getProvider(file);
      presentation.setEnabled(browserUrlProvider != null);

      if (browserUrlProvider != null) {
        final String actionText = browserUrlProvider.getOpenInBrowserActionText(file);
        if (actionText != null) {
          presentation.setText(actionText);
        }
        final String description = browserUrlProvider.getOpenInBrowserActionDescription(file);
        if (description != null) {
          presentation.setDescription(description);
        }
      }

      if (ActionPlaces.isPopupPlace(e.getPlace())) {
        presentation.setVisible(presentation.isEnabled());
      }
    } else {
      presentation.setVisible(false);
      presentation.setEnabled(false);
    }
  }

  public void actionPerformed(AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    final PsiFile psiFile = LangDataKeys.PSI_FILE.getData(dataContext);
    LOG.assertTrue(psiFile != null);
    final WebBrowserUrlProvider provider = WebBrowserUrlProviders.getProvider(psiFile);
    if (provider != null) {
      try {
        final String url = provider.getUrl(psiFile, false);
        ApplicationManager.getApplication().saveAll();
        BrowserUtil.launchBrowser(url);
      }
      catch (WebBrowserUrlProvider.BrowserException e1) {
        Messages.showErrorDialog(e1.getMessage(), XmlBundle.message("browser.error"));
      }
      catch (Exception e1) {
        LOG.error(e1);
      }
    }
  }
}
