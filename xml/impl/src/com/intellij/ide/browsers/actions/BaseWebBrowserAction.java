package com.intellij.ide.browsers.actions;

import com.intellij.ide.browsers.OpenInBrowserRequest;
import com.intellij.ide.browsers.WebBrowser;
import com.intellij.ide.browsers.WebBrowserManager;
import com.intellij.ide.browsers.WebBrowserUrlProvider;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.util.Pair;
import com.intellij.xml.util.HtmlUtil;
import org.jetbrains.annotations.NotNull;

final class BaseWebBrowserAction extends DumbAwareAction {
  private final WebBrowser browser;

  public BaseWebBrowserAction(@NotNull WebBrowser browser) {
    super(browser.getName(), null, browser.getIcon());

    this.browser = browser;
  }

  @Override
  public void update(final AnActionEvent e) {
    if (!WebBrowserManager.getInstance().getBrowserSettings(browser).isActive()) {
      e.getPresentation().setEnabled(false);
      e.getPresentation().setVisible(false);
      return;
    }

    Pair<OpenInBrowserRequest, WebBrowserUrlProvider> result = OpenInBrowserBaseGroupAction.doUpdate(e);
    if (result == null) {
      return;
    }

    String description = getTemplatePresentation().getText();
    if (ActionPlaces.CONTEXT_TOOLBAR.equals(e.getPlace())) {
      StringBuilder builder = new StringBuilder(description);
      builder.append(" (");
      Shortcut[] shortcuts = KeymapManager.getInstance().getActiveKeymap().getShortcuts("WebOpenInAction");
      if (shortcuts.length > 0) {
        builder.append(KeymapUtil.getShortcutText(shortcuts[0]));
      }

      if (HtmlUtil.isHtmlFile(result.first.getFile())) {
        builder.append(", hold Shift to open URL of local file");
      }
      builder.append(')');
      description = builder.toString();
    }
    e.getPresentation().setText(description);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    OpenFileInDefaultBrowserAction.open(e, browser);
  }
}