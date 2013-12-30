package com.intellij.ide.browsers.actions;

import com.intellij.ide.browsers.BrowsersConfiguration;
import com.intellij.ide.browsers.WebBrowser;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class OpenInBrowserGroupAction extends ActionGroup implements DumbAware {
  private AnAction[] myActions;

  public OpenInBrowserGroupAction() {
    super(null, true);
  }

  @NotNull
  @Override
  public AnAction[] getChildren(@Nullable AnActionEvent e) {
    if (myActions == null) {
      myActions = computeActions();
    }
    return myActions;
  }

  @NotNull
  private static AnAction[] computeActions() {
    List<WebBrowser> browsers = BrowsersConfiguration.getInstance().getActive();
    AnAction[] actions = new AnAction[browsers.size()];
    for (int i = 0, size = browsers.size(); i < size; i++) {
      WebBrowser browser = browsers.get(i);
      actions[i] = new BaseWebBrowserAction(browser);
    }
    return actions;
  }
}