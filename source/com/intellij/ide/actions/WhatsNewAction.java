package com.intellij.ide.actions;

import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;

/**
 * @author max
 */
public class WhatsNewAction extends AnAction implements DumbAware {
  public void actionPerformed(AnActionEvent e) {
    BrowserUtil.launchBrowser("http://www.jetbrains.com/idea/features/newfeatures.html");
  }
}
